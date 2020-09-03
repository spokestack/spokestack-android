package io.spokestack.spokestack.nlu.tensorflow;

import android.os.SystemClock;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUContext;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.NLUService;
import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.util.TraceListener;
import io.spokestack.spokestack.nlu.tensorflow.parsers.DigitsParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.IdentityParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.IntegerParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.SelsetParser;
import io.spokestack.spokestack.tensorflow.TensorflowModel;
import io.spokestack.spokestack.util.AsyncResult;
import io.spokestack.spokestack.util.EventTracer;
import io.spokestack.spokestack.util.Tuple;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * On-device natural language understanding powered by DistilBERT and TensorFlow
 * Lite.
 *
 * <p>
 * This component uses a pre-trained TensorFlow Lite model to classify user
 * utterances into intents and extract slot values. Metadata supplied alongside
 * the model is used to determine the types of any slot values detected.
 * Classification is performed on a background thread.
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
 * </ul>
 */
public final class TensorflowNLU implements NLUService {
    private final ExecutorService executor =
          Executors.newSingleThreadExecutor();
    private final TextEncoder textEncoder;

    private TensorflowModel nluModel = null;
    private TFNLUOutput outputParser = null;
    private NLUContext context;
    private int maxTokens;
    private int sepTokenId;
    private int padTokenId;

    private Thread loadThread;
    private volatile boolean ready = false;

    /**
     * Create a new NLU instance, automatically loading the TensorFlow model
     * metadata in the background. Any errors encountered during loading will be
     * reported to registered {@link TraceListener}s.
     *
     * @param builder builder with configuration parameters
     */
    private TensorflowNLU(Builder builder) {
        String modelPath = builder.config.getString("nlu-model-path");
        String metadataPath = builder.config.getString("nlu-metadata-path");
        this.context = builder.context;
        this.textEncoder = builder.textEncoder;
        this.loadThread = builder.threadFactory.newThread(
              () -> {
                  loadModel(builder.modelLoader, metadataPath, modelPath);
                  initParsers(builder.slotParserClasses);
              });
        this.loadThread.start();
        this.padTokenId = this.textEncoder.encodeSingle("[PAD]");
        this.sepTokenId = this.textEncoder.encodeSingle("[SEP]");
    }

    private void initParsers(Map<String, String> parserClasses) {
        Map<String, SlotParser> slotParsers = new HashMap<>();
        for (String slotType : parserClasses.keySet()) {
            try {
                SlotParser parser = (SlotParser) Class
                      .forName(parserClasses.get(slotType))
                      .getConstructor()
                      .newInstance();
                slotParsers.put(slotType, parser);
                this.outputParser.registerSlotParsers(slotParsers);
            } catch (Exception e) {
                this.context.traceError("Error loading slot parsers: %s",
                      e.getLocalizedMessage());
            }
        }
    }

    private void loadModel(TensorflowModel.Loader loader,
                           String metadataPath,
                           String modelPath) {
        try (FileReader fileReader = new FileReader(metadataPath);
             JsonReader reader = new JsonReader(fileReader)) {
            Gson gson = new Gson();
            Metadata metadata = gson.fromJson(reader, Metadata.class);

            this.nluModel = loader
                  .setPath(modelPath)
                  .load();
            this.maxTokens = this.nluModel.inputs(0).capacity()
                  / this.nluModel.getInputSize();
            this.outputParser = new TFNLUOutput(metadata);
            warmup();
            this.ready = true;
        } catch (IOException e) {
            this.context.traceError("Error loading NLU model: %s",
                  e.getLocalizedMessage());
        }
    }

    private void warmup() {
        this.nluModel.inputs(0).rewind();
        for (int i = 0; i < maxTokens; i++) {
            this.nluModel.inputs(0).putInt(0);
        }
        this.nluModel.run();
    }

    /**
     * @return The maximum number of tokens the model can accept. Used for
     * testing.
     */
    int getMaxTokens() {
        return this.maxTokens;
    }

    /**
     * Classify a user utterance, returning a wrapper that can either block
     * until the classification is complete or call a registered callback when
     * the result is ready.
     *
     * @param utterance The utterance to classify.
     * @return An object representing the result of the asynchronous
     * classification.
     */
    public AsyncResult<NLUResult> classify(String utterance) {
        return classify(utterance, this.context);
    }

    @Override
    public AsyncResult<NLUResult> classify(String utterance,
                                           NLUContext nluContext) {
        ensureReady();
        AsyncResult<NLUResult> asyncResult = new AsyncResult<>(
              () -> {
                  try {
                      long start = SystemClock.elapsedRealtime();
                      NLUResult result = tfClassify(utterance, nluContext);
                      if (nluContext.canTrace(EventTracer.Level.PERF)) {
                          nluContext.tracePerf("Classification: %5dms",
                                (SystemClock.elapsedRealtime() - start));
                      }
                      return result;
                  } catch (Exception e) {
                      return new NLUResult.Builder(utterance)
                            .withError(e)
                            .build();
                  } finally {
                      nluContext.reset();
                  }
              });
        this.executor.submit(asyncResult);
        return asyncResult;
    }

    private void ensureReady() {
        if (!this.ready) {
            try {
                this.loadThread.join();
            } catch (InterruptedException e) {
                this.context.traceError("Interrupted during loading");
            }
        }
    }

    private NLUResult tfClassify(String utterance, NLUContext nluContext) {
        EncodedTokens encoded = this.textEncoder.encode(utterance);
        nluContext.traceDebug("Token IDs: %s", encoded.getIds());

        int[] tokenIds = pad(encoded.getIds());
        this.nluModel.inputs(0).rewind();
        for (int tokenId : tokenIds) {
            this.nluModel.inputs(0).putInt(tokenId);
        }

        long start = SystemClock.elapsedRealtime();
        this.nluModel.run();
        if (nluContext.canTrace(EventTracer.Level.PERF)) {
            nluContext.tracePerf("Inference: %5dms",
                  (SystemClock.elapsedRealtime() - start));
        }

        // interpret model outputs
        Tuple<Metadata.Intent, Float> prediction = outputParser.getIntent(
              this.nluModel.outputs(0));
        Metadata.Intent intent = prediction.first();
        nluContext.traceDebug("Intent: %s", intent.getName());

        Map<String, String> slots = outputParser.getSlots(
              nluContext,
              encoded,
              this.nluModel.outputs(1));
        Map<String, Slot> parsedSlots = outputParser.parseSlots(intent, slots);
        nluContext.traceDebug("Slots: %s", parsedSlots.toString());

        return new NLUResult.Builder(utterance)
              .withIntent(intent.getName())
              .withConfidence(prediction.second())
              .withSlots(parsedSlots)
              .build();
    }

    private int[] pad(List<Integer> ids) {
        if (ids.size() > this.maxTokens) {
            throw new IllegalArgumentException(
                  "input: " + ids.size() + " tokens; max input length is: "
                        + this.maxTokens);
        }
        int[] padded = new int[this.maxTokens];
        for (int i = 0; i < ids.size(); i++) {
            padded[i] = ids.get(i);
        }
        if (ids.size() < this.maxTokens) {
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

    /**
     * Fluent builder interface for initializing a TensorFlow NLU model.
     */
    public static class Builder {
        private NLUContext context;
        private List<TraceListener> traceListeners = new ArrayList<>();
        private Map<String, String> slotParserClasses = new HashMap<>();
        private SpeechConfig config = new SpeechConfig();
        private TensorflowModel.Loader modelLoader;
        private ThreadFactory threadFactory;
        private TextEncoder textEncoder;

        /**
         * Creates a new builder instance.
         */
        public Builder() {
            config.put("trace-level", EventTracer.Level.ERROR.value());
            registerSlotParser("digits", DigitsParser.class.getName());
            registerSlotParser("integer", IntegerParser.class.getName());
            registerSlotParser("selset", SelsetParser.class.getName());
            registerSlotParser("entity", IdentityParser.class.getName());
        }

        /**
         * Set the thread factory used to create the NLU model's loader thread.
         * Used for testing.
         *
         * @param factory The thread factory to use for loading the NLU model.
         * @return this
         */
        Builder setThreadFactory(ThreadFactory factory) {
            this.threadFactory = factory;
            return this;
        }

        /**
         * Attaches a configuration object, overwriting any existing
         * configuration.
         *
         * @param value configuration to attach
         * @return this
         */
        public Builder setConfig(SpeechConfig value) {
            this.config = value;
            return this;
        }

        /**
         * Attaches a model loader. Used for testing.
         *
         * @param loader The TensorFlow model loader to use.
         * @return this
         */
        public Builder setModelLoader(TensorflowModel.Loader loader) {
            this.modelLoader = loader;
            return this;
        }

        /**
         * Attaches a text encoder. Used for testing.
         *
         * @param encoder The text encoder to use.
         * @return this
         */
        public Builder setTextEncoder(TextEncoder encoder) {
            this.textEncoder = encoder;
            return this;
        }

        /**
         * Sets a configuration value.
         *
         * @param key   configuration property name
         * @param value property value
         * @return this
         */
        public Builder setProperty(String key, Object value) {
            config.put(key, value);
            return this;
        }

        /**
         * Register a custom parser for a slot of a specified type.
         *
         * @param slotType    The type of slot that should be parsed by {@code
         *                    parserClass}.
         * @param parserClass The name of the class responsible for parsing
         *                    slots of {@code slotType}.
         * @return this
         */
        public Builder registerSlotParser(String slotType,
                                          String parserClass) {
            this.slotParserClasses.put(slotType, parserClass);
            return this;
        }

        /**
         * Adds a trace listener to receive events from the NLU system.
         *
         * @param listener the listener to register
         * @return this
         */
        public Builder addTraceListener(TraceListener listener) {
            this.traceListeners.add(listener);
            return this;
        }

        /**
         * Create a new NLU instance, automatically loading the TensorFlow model
         * metadata in the background. Any errors encountered during loading
         * will be reported to registered {@link TraceListener}s.
         *
         * @return An initialized {@code TensorflowNLU} instance
         */
        public TensorflowNLU build() {
            this.context = new NLUContext(this.config);
            for (TraceListener listener : this.traceListeners) {
                this.context.addTraceListener(listener);
            }
            if (modelLoader == null) {
                modelLoader = new TensorflowModel.Loader();
            }
            if (textEncoder == null) {
                textEncoder =
                      new WordpieceTextEncoder(this.config, this.context);
            }
            if (threadFactory == null) {
                threadFactory = Thread::new;
            }
            return new TensorflowNLU(this);
        }

    }
}
