package io.spokestack.spokestack.nlu.tensorflow;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.NLUService;
import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.tensorflow.TensorflowModel;

import java.io.FileReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * On-device natural language understanding powered by DistilBERT and TensorFLow
 * Lite.
 *
 * <p>
 * This component uses a pre-trained TensorFlow Lite model to classify user
 * utterances into intents and extract slot values. Metadata supplied alongside
 * the model is used to determine the types of any slot values detected. Both
 * model loading and classification are performed on a background thread.
 * </p>
 *
 * <p>
 * This component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>nlu-model-path</b> (string, required): file system path to the NLU
 *      TensorFlow Lite model.
 *   </li>
 *   <li>
 *      <b>nlu-metadata-path</b> (string, required): file system path to the
 *      model's metadata, used to decode intent and slot names and types.
 *   </li>
 *   <li>
 *      <b>wordpiece-vocab-path</b> (string, required): file system path to the
 *      wordpiece vocabulary file used by the wordpiece token encoder.
 *   </li>
 *   <li>
 *      <b>nlu-input-length</b> (integer, optional): Padded length of the
 *      model's input sequences. Defaults to 128 and should only be changed if
 *      this parameter is explicitly set to a different value at training time.
 *   </li>
 * </ul>
 */
public class TensorflowNLU implements NLUService {

    private static final int DEFAULT_TOKEN_LENGTH = 128;

    private final ExecutorService executor =
          Executors.newSingleThreadExecutor();
    private final TextEncoder textEncoder;

    private TensorflowModel nluModel = null;
    private int tokenLength;
    private int sepTokenId;
    private int padTokenId;
    private Metadata metadata;

    /**
     * Create a new NLU instance, automatically loading the TensorFlow model in
     * the background.
     *
     * @param config Configuration for this component.
     * @throws Exception If there is an error loading the model's metadata.
     */
    public TensorflowNLU(SpeechConfig config) throws Exception {
        this(config, new TensorflowModel.Loader(),
              new WordpieceTextEncoder(config));
    }

    /**
     * Create a new NLU instance. Used for testing.
     *
     * @param config  Configuration for this component.
     * @param loader  Tensorflow model loader.
     * @param encoder Tokenizer used to create model inputs from raw text.
     * @throws Exception If there is an error loading the model's metadata.
     */
    TensorflowNLU(SpeechConfig config, TensorflowModel.Loader loader,
                  TextEncoder encoder)
          throws Exception {
        String modelPath = config.getString("nlu-model-path");
        String metadataPath = config.getString("nlu-metadata-path");
        this.tokenLength = config.getInteger("nlu-input-length",
              DEFAULT_TOKEN_LENGTH);
        this.textEncoder = encoder;
        this.padTokenId = this.textEncoder.encodeSingle("[PAD]");
        this.sepTokenId = this.textEncoder.encodeSingle("[SEP]");
        FileReader reader = new FileReader(metadataPath);
        this.executor.execute(() -> {
            loadMetadata(reader);
            loadModel(loader, modelPath);
        });
    }

    private void loadMetadata(Reader fileReader) {
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(fileReader);
        this.metadata = gson.fromJson(reader, Metadata.class);
    }

    private void loadModel(TensorflowModel.Loader loader,
                           String modelPath) {
        this.nluModel = loader
              .setPath(modelPath)
              .load();
        this.tokenLength = this.nluModel.inputs(0).capacity()
              / this.metadata.getIntents().length
              / this.nluModel.getInputSize();
    }

    @Override
    public Future<NLUResult> classify(String utterance,
                                      Map<String, Object> context) {
        return this.executor.submit(() -> {
            try {
                return tfClassify(utterance);
            } catch (Exception e) {
                return new NLUResult.Builder(utterance)
                      .withError(e)
                      .build();
            }
        });
    }

    private NLUResult tfClassify(String utterance) {
        EncodedTokens encoded = this.textEncoder.encode(utterance);
        int[] tokenIds = pad(encoded.getIds());
        this.nluModel.inputs(0).rewind();
        for (int tokenId : tokenIds) {
            this.nluModel.inputs(0).putInt(tokenId);
        }

        this.nluModel.run();

        // interpret model outputs
        Metadata.Intent intent = getIntent(this.nluModel.outputs(0));
        Map<String, Slot> slots = getSlots(
              this.nluModel.outputs(1),
              intent,
              encoded);

        return new NLUResult.Builder(utterance)
              .withIntent(intent.getName())
              .withSlots(slots)
              .build();
    }

    private int[] pad(List<Integer> ids) {
        int[] padded = new int[this.tokenLength];
        for (int i = 0; i < ids.size(); i++) {
            padded[i] = ids.get(i);
        }
        if (ids.size() < this.tokenLength) {
            padded[ids.size()] = sepTokenId;
            // if padTokenId is 0, we can rely on the fact that that's the
            // default value for primitive ints and not bother re-filling the
            // array in a loop
            if (padTokenId != 0) {
                for (int i = ids.size() + 2; i < padded.length; i++) {
                    padded[i] = padTokenId;
                }
            }
        }
        return padded;
    }

    private Metadata.Intent getIntent(ByteBuffer output) {
        float[] posteriors = new float[this.metadata.getIntents().length];
        for (int i = 0; i < posteriors.length; i++) {
            posteriors[i] = output.getFloat();
        }
        int index = argMax(posteriors);
        return this.metadata.getIntents()[index];
    }

    private Map<String, Slot> getSlots(ByteBuffer output,
                                       Metadata.Intent intent,
                                       EncodedTokens encoded) {
        int numTokens = encoded.getIds().size();
        String[] labels = getLabels(output, numTokens);
        return parseSlots(intent, labels, encoded);
    }

    private String[] getLabels(ByteBuffer output, int numTokens) {
        int numTags = this.metadata.getTags().length;
        String[] labels = new String[numTokens];
        for (int i = 0; i < labels.length; i++) {
            float[] posteriors = new float[numTags];
            for (int j = 0; j < numTags; j++) {
                posteriors[j] = output.getFloat();
            }
            int index = argMax(posteriors);
            labels[i] = this.metadata.getTags()[index];
        }
        return labels;
    }

    private int argMax(float[] values) {
        int maxIndex = 0;
        float maxValue = values[0];

        for (int i = 1; i < values.length; i++) {
            float curVal = values[i];
            if (curVal > maxValue) {
                maxIndex = i;
                maxValue = curVal;
            }
        }
        return maxIndex;
    }

    private Map<String, Slot> parseSlots(
          Metadata.Intent intent,
          String[] labels,
          EncodedTokens encoded) {
        Map<String, Slot> slots = new HashMap<>();
        String curSlot = null;
        int[] slotTokenRange = new int[2];
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (label.equals("o")) {
                if (curSlot != null) {
                    String text = encoded.decodeRange(slotTokenRange[0],
                          slotTokenRange[1] + 1);
                    parseSlot(slots, curSlot, intent, text);
                    curSlot = null;
                }
            } else {
                if (label.startsWith("b")) {
                    // two distinct slots, the first one word long, adjacent to
                    // each other
                    if (curSlot != null) {
                        String text = encoded.decodeRange(slotTokenRange[0],
                              i + 1);
                        parseSlot(slots, curSlot, intent, text);
                    }
                    slotTokenRange[0] = i;
                    curSlot = label.substring(2);
                }

                // add both b_ and i_ tagged tokens to the current slot
                slotTokenRange[1] = i;
            }

        }
        if (curSlot != null) {
            String text = encoded.decodeRange(slotTokenRange[0],
                  slotTokenRange[1] + 1);
            parseSlot(slots, curSlot, intent, text);
        }
        return slots;
    }

    private void parseSlot(Map<String, Slot> slots,
                           String slotName,
                           Metadata.Intent intent,
                           String slotValue) {
        Metadata.Slot metaSlot = null;
        for (Metadata.Slot slot : intent.getSlots()) {
            if (slot.getName().equals(slotName)) {
                metaSlot = slot;
                break;
            }
        }
        if (metaSlot == null) {
            String message = String.format("no %s slot in %s intent", slotName,
                  intent.getName());
            throw new IllegalArgumentException(message);
        }

        slots.put(
              slotName,
              Slot.parse(metaSlot.getType(), slotName, slotValue));
    }
}
