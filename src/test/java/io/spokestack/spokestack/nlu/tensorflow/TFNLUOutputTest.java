package io.spokestack.spokestack.nlu.tensorflow;

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
              .allocateDirect(5 * metadata.getTags().length * 4)
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
        NLUContext context = new NLUContext(new SpeechConfig());
        // no slots, null implicit_slots
        Metadata.Intent intent = getTargetIntent("accept");

        TFNLUOutput outputParser = new TFNLUOutput(metadata);
        outputParser.registerSlotParsers(getSlotParsers());

        Map<String, Slot> expected = new HashMap<>();
        Map<String, Slot> result =
              outputParser.parseSlots(context, intent, new HashMap<>());
        assertEquals(expected, result);

        intent = getTargetIntent("slot_features");

        Map<String, String> slotValues = new HashMap<>();
        slotValues.put("feature_2", "9");

        expected = new HashMap<>();
        Slot implicitSlot = new Slot("feature_1", "default", "default");
        // no value is present in the output, so the implicit value is used
        expected.put("feature_1", implicitSlot);
        // note the name override
        Slot captureNameSlot = new Slot("test_num", "9", 9);
        expected.put("test_num", captureNameSlot);

        result = outputParser.parseSlots(context, intent, slotValues);
        assertEquals(expected, result);

        // explicit values override implicit ones
        slotValues = new HashMap<>();
        slotValues.put("feature_1", "overridden");

        expected = new HashMap<>();
        implicitSlot = new Slot("feature_1", "overridden", "overridden");
        expected.put("feature_1", implicitSlot);

        result = outputParser.parseSlots(context, intent, slotValues);
        assertEquals(expected, result);
    }

    @Test
    public void testSpuriousSlot() {
        SpeechConfig config = new SpeechConfig();
        config.put("trace-level", 0);
        NLUContext context = new NLUContext(config);
        TraceListener listener = new TraceListener();
        context.addTraceListener(listener);
        Metadata.Intent intent = getTargetIntent("accept");

        Map<String, Slot> expected = new HashMap<>();
        expected.put("extra", new Slot("extra", "extraValue", "extraValue"));
        Map<String, String> slotVals = new HashMap<>();
        slotVals.put("extra", "extraValue");
        Map<String, Slot> result =
              outputParser.parseSlots(context, intent, slotVals);
        assertEquals(expected, result);
        // check the warning message
        assertEquals(EventTracer.Level.WARN, listener.lastLevel);
        assertEquals("no extra slot in accept intent", listener.lastMessage);
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
        public void onTrace(EventTracer.Level level, String message) {
           this.lastLevel = level;
           this.lastMessage = message;
        }
    }
}
