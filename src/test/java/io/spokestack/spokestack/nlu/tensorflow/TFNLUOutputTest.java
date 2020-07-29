package io.spokestack.spokestack.nlu.tensorflow;

import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUContext;
import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.nlu.tensorflow.parsers.IdentityParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.IntegerParser;
import io.spokestack.spokestack.util.EventTracer;
import io.spokestack.spokestack.util.Tuple;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TFNLUOutputTest {

    private Metadata metadata;
    private TFNLUOutput outputParser;

    @Before
    public void before() throws FileNotFoundException {
        Gson gson = new Gson();
        Reader reader = new FileReader("src/test/resources/nlu.json");
        JsonReader jsonReader = new JsonReader(reader);
        metadata = gson.fromJson(jsonReader, Metadata.class);
        outputParser = new TFNLUOutput(metadata);
    }

    @Test
    public void getIntent() {
        ByteBuffer output = ByteBuffer
              .allocateDirect(4 * metadata.getIntents().length * 4)
              .order(ByteOrder.nativeOrder());
        output.rewind();
        for (Float val : Arrays.asList(0.0f, 0.0f, 0.0f, 10.0f)) {
            output.putFloat(val);
        }
        output.rewind();

        Metadata.Intent intent = getTargetIntent("slot_features");

        Tuple<Metadata.Intent, Float> result = outputParser.getIntent(output);

        assertEquals(intent.getName(), result.first().getName());
        assertEquals((Float) 10.0f, result.second());
    }

    @Test
    public void getSlots() {
        ByteBuffer output = ByteBuffer
              .allocateDirect(6 * metadata.getTags().length * 4)
              .order(ByteOrder.nativeOrder());
        output.rewind();

        List<Float> tagPosteriors =
              setTagPosteriors(
                    Arrays.asList("o", "b_feature_1", "o", "o", "b_feature_2"));
        for (Float val : tagPosteriors) {
            output.putFloat(val);
        }
        output.rewind();

        Map<String, String> expected = new HashMap<>();
        expected.put("feature_1", "utterance");
        expected.put("feature_2", "2");

        NLUContext context = new NLUContext(new SpeechConfig());
        String utterance = "an utterance for test 2";
        String[] split = utterance.split(" ");
        EncodedTokens encoded = new EncodedTokens(split);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < split.length; i++) {
            indices.add(i);
        }
        encoded.addTokenIds(indices);
        encoded.setOriginalIndices(indices);

        Map<String, String> result =
              outputParser.getSlots(context, encoded, output);
        assertEquals(expected, result);

        // invalid but parseable tag sequence
        tagPosteriors = setTagPosteriors(
              Arrays.asList("o", "i_feature_1", "i_feature_1", "o", "o", "b_feature_2"));
        output.rewind();
        for (Float val : tagPosteriors) {
            output.putFloat(val);
        }
        output.rewind();

        expected = new HashMap<>();
        expected.put("feature_1", "longer utterance");
        expected.put("feature_2", "2");

        context = new NLUContext(new SpeechConfig());
        utterance = "a longer utterance for test 2";
        split = utterance.split(" ");
        encoded = new EncodedTokens(split);
        indices = new ArrayList<>();
        for (int i = 0; i < split.length; i++) {
            indices.add(i);
        }
        encoded.addTokenIds(indices);
        encoded.setOriginalIndices(indices);

        result = outputParser.getSlots(context, encoded, output);
        assertEquals(expected, result);
    }

    private List<Float> setTagPosteriors(List<String> tags) {
        List<Float> posteriors = new ArrayList<>();
        for (String desired : tags) {
            for (String tag : metadata.getTags()) {
                if (tag.equals(desired)) {
                    posteriors.add(10.0f);
                } else {
                    posteriors.add(0.0f);
                }
            }
        }
        return posteriors;
    }

    @Test
    public void parseSlots() {
        // no slots, null implicit_slots
        Metadata.Intent intent = getTargetIntent("accept");

        TFNLUOutput outputParser = new TFNLUOutput(metadata);
        outputParser.registerSlotParsers(getSlotParsers());

        Map<String, Slot> expected = new HashMap<>();
        Map<String, Slot> result =
              outputParser.parseSlots(intent, new HashMap<>());
        assertEquals(expected, result);

        intent = getTargetIntent("slot_features");

        Map<String, String> slotValues = new HashMap<>();
        slotValues.put("feature_2", "9");

        expected = new HashMap<>();
        Slot implicitSlot =
              new Slot("feature_1", "entity", "default", "default");
        // no value is present in the output, so the implicit value is used
        expected.put("feature_1", implicitSlot);
        // note the name override
        Slot captureNameSlot = new Slot("test_num", "integer", "9", 9);
        expected.put("test_num", captureNameSlot);

        result = outputParser.parseSlots(intent, slotValues);
        assertEquals(expected, result);

        // explicit values override implicit ones
        slotValues = new HashMap<>();
        slotValues.put("feature_1", "overridden");

        expected = new HashMap<>();
        implicitSlot =
              new Slot("feature_1", "entity", "overridden", "overridden");
        expected.put("feature_1", implicitSlot);

        // not present in slot tagger output, but included in client output
        expected.put("test_num", new Slot("test_num", "integer", null, null));

        result = outputParser.parseSlots(intent, slotValues);
        assertEquals(expected, result);
    }

    @Test
    public void testSpuriousSlot() {
        Metadata.Intent intent = getTargetIntent("accept");
        Map<String, String> slotVals = new HashMap<>();
        slotVals.put("extra", "extraValue");
        Map<String, Slot> result = outputParser.parseSlots(intent, slotVals);
        assertTrue(result.isEmpty());
    }

    private Metadata.Intent getTargetIntent(String name) {
        for (Metadata.Intent in : metadata.getIntents()) {
            if (in.getName().equals(name)) {
                return in;
            }
        }
        throw new IllegalArgumentException("intent " + name + " not found!");
    }

    private Map<String, SlotParser> getSlotParsers() {
        Map<String, SlotParser> parsers = new HashMap<>();
        parsers.put("integer", new IntegerParser());
        parsers.put("entity", new IdentityParser());
        return parsers;
    }

    static class TraceListener
          implements io.spokestack.spokestack.nlu.TraceListener {

        private EventTracer.Level lastLevel;
        private String lastMessage;

        @Override
        public void onTrace(@NonNull EventTracer.Level level, @NonNull String message) {
            this.lastLevel = level;
            this.lastMessage = message;
        }
    }
}
