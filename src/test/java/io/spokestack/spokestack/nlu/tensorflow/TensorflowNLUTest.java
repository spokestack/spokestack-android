package io.spokestack.spokestack.nlu.tensorflow;

import android.os.SystemClock;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.util.EventTracer;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.spokestack.spokestack.nlu.tensorflow.NLUTestUtils.TestEnv;
import static io.spokestack.spokestack.nlu.tensorflow.NLUTestUtils.testConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SystemClock.class)
public class TensorflowNLUTest {

    @Before
    public void before() {
        mockStatic(SystemClock.class);
    }

    @Test
    public void initialization() throws Exception {
        SpeechConfig config = testConfig();

        // default config throws no errors
        new TestEnv(config);

        AtomicBoolean loadError = new AtomicBoolean(false);
        ControllableFactory factory = new ControllableFactory();
        String brokenParserClass = "io.spokestack.spokestack.nlu.tensorflow."
              + "TensorflowNLUTest$NoInitParser";

        // error loading parser
        TestEnv env = new TestEnv(config);
        env.nluBuilder
              .setThreadFactory(factory)
              .registerSlotParser("integer", brokenParserClass)
              .addTraceListener((level, message) -> {
                  if (level.equals(EventTracer.Level.ERROR)) {
                      loadError.set(true);
                  }
              });
        env.nluBuilder.build();
        factory.theOneThread.join();
        assertTrue(loadError.get());
    }

    @Test
    public void classify() throws Exception {
        TestEnv env = new TestEnv(testConfig());

        String utterance = "error";
        NLUResult result = env.classify(utterance).get();
        assertEquals(IllegalStateException.class, result.getError().getClass());
        assertEquals(utterance, result.getUtterance());
        assertEquals(0, result.getConfidence());
        assertNull(result.getIntent());
        assertTrue(result.getContext().isEmpty());
        assertTrue(result.getSlots().isEmpty());

        StringBuilder tooManyTokens = new StringBuilder();
        for (int i = 0; i <= env.nlu.getMaxTokens(); i++) {
            tooManyTokens.append("a ");
        }
        utterance = tooManyTokens.toString();
        result = env.classify(utterance).get();
        assertEquals(IllegalArgumentException.class,
              result.getError().getClass());
        assertEquals(utterance, result.getUtterance());
        assertEquals(0, result.getConfidence());
        assertNull(result.getIntent());
        assertTrue(result.getContext().isEmpty());
        assertTrue(result.getSlots().isEmpty());

        utterance = "this code is for test 1";
        float conf = 0.75f;
        float[] intentResult =
              buildIntentResult(2, env.metadata.getIntents().length, conf);
        float[] tagResult =
              new float[utterance.split(" ").length
                    * env.metadata.getTags().length];
        setTag(tagResult, env.metadata.getTags().length, 0, 1);
        setTag(tagResult, env.metadata.getTags().length, 1, 2);
        setTag(tagResult, env.metadata.getTags().length, 5, 3);
        env.testModel.setOutputs(intentResult, tagResult);
        result = env.classify(utterance).get();

        Map<String, Slot> slots = new HashMap<>();
        slots.put("noun_phrase",
              new Slot("noun_phrase", "entity", "this code", "this code"));
        slots.put("test_num", new Slot("test_num", "integer", "1", 1));

        assertNull(result.getError());
        assertEquals("describe_test", result.getIntent());
        assertEquals(conf, result.getConfidence());
        for (String slotName : slots.keySet()) {
            assertEquals(slots.get(slotName), result.getSlots().get(slotName));
        }
        assertEquals(slots, result.getSlots());
        assertEquals(utterance, result.getUtterance());
        assertTrue(result.getContext().isEmpty());

        // simulate two different spans being tagged as the same slot
        // in this example, "bad" doesn't get tagged as part of the noun phrase
        // (which is incorrect, but we're just testing the slot extraction
        // logic here)
        utterance = "this bad code is for test 1";
        intentResult =
              buildIntentResult(2, env.metadata.getIntents().length, conf);
        tagResult = new float[utterance.split(" ").length
              * env.metadata.getTags().length];
        setTag(tagResult, env.metadata.getTags().length, 0, 1);
        setTag(tagResult, env.metadata.getTags().length, 2, 1);
        setTag(tagResult, env.metadata.getTags().length, 6, 3);
        env.testModel.setOutputs(intentResult, tagResult);
        result = env.classify(utterance).get();

        slots = new HashMap<>();
        slots.put("noun_phrase",
              new Slot("noun_phrase", "entity", "this code", "this code"));
        slots.put("test_num", new Slot("test_num", "integer", "1", 1));

        assertNull(result.getError());
        assertEquals("describe_test", result.getIntent());
        assertEquals(conf, result.getConfidence());
        for (String slotName : slots.keySet()) {
            assertEquals(slots.get(slotName), result.getSlots().get(slotName));
        }
        assertEquals(slots, result.getSlots());
        assertEquals(utterance, result.getUtterance());
        assertTrue(result.getContext().isEmpty());
    }

    @Test
    public void testConfidenceThreshold() throws Exception {
        TestEnv env = new TestEnv(testConfig());
        env.nluBuilder.setConfidenceThreshold(0.5f, "fallback");

        String utterance = "how far is it to the moon?";
        float conf = 0.3f;
        float[] intentResult =
              buildIntentResult(1, env.metadata.getIntents().length, conf);

        // include some tags in the result to make sure they're ignored
        float[] tagResult =
              new float[utterance.split(" ").length
                    * env.metadata.getTags().length];
        setTag(tagResult, env.metadata.getTags().length, 0, 1);
        setTag(tagResult, env.metadata.getTags().length, 1, 2);
        env.testModel.setOutputs(intentResult, tagResult);
        NLUResult result = env.classify(utterance).get();

        assertNull(result.getError());
        assertEquals("fallback", result.getIntent());
        assertEquals(conf, result.getConfidence());
        assertTrue(result.getSlots().isEmpty());
        assertEquals(utterance, result.getUtterance());
        assertTrue(result.getContext().isEmpty());
    }

    private float[] buildIntentResult(int index, int numIntents,
                                      float confidence) {
        float[] result = new float[numIntents];
        result[index] = confidence;
        return result;
    }

    private void setTag(float[] tagResult, int numTags,
                        int tokenIndex, int tagIndex) {
        tagResult[tokenIndex * numTags + tagIndex] = 10;
    }

    static class ControllableFactory implements ThreadFactory {
        private Thread theOneThread;

        @Override
        public Thread newThread(@NotNull Runnable r) {
            theOneThread = new Thread(r);
            return theOneThread;
        }
    }

    static class NoInitParser implements SlotParser {

        NoInitParser() {
            throw new NullPointerException();
        }

        @Override
        public Object parse(Map<String, Object> metadata, String rawValue)
              throws Exception {
            return null;
        }
    }
}
