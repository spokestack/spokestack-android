package io.spokestack.spokestack.nlu.tensorflow;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.tensorflow.TensorflowModel;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TensorflowNLUTest {

    @Test
    public void classify() throws Exception {
        TestEnv env = new TestEnv(testConfig());

        String utterance = "error";
        NLUResult result = env.classify(utterance).get();
        assertEquals(IllegalStateException.class, result.getError().getClass());
        assertEquals(utterance, result.getUtterance());
        assertNull(result.getIntent());
        assertNull(result.getSlots());
        assertNull(result.getContext());

        utterance = "this code is for test 1";
        float[] intentResult =
              buildIntentResult(2, env.metadata.getIntents().length);
        float[] tagResult =
              new float[utterance.split(" ").length * env.metadata.getTags().length];
        setTag(tagResult, env.metadata.getTags().length, 0, 1);
        setTag(tagResult, env.metadata.getTags().length, 1, 2);
        setTag(tagResult, env.metadata.getTags().length, 5, 3);
        env.testModel.setOutputs(intentResult, tagResult);
        result = env.classify(utterance).get();

        Map<String, Slot> slots = new HashMap<>();
        slots.put("noun_phrase", new Slot("noun_phrase", "this code"));
        slots.put("test_num", new Slot("test_num", 1));

        assertNull(result.getError());
        assertEquals("describe_test", result.getIntent());
        assertEquals(slots, result.getSlots());
        assertEquals(utterance, result.getUtterance());
        assertTrue(result.getContext().isEmpty());
    }

    private float[] buildIntentResult(int index, int numIntents) {
        float[] result = new float[numIntents];
        result[index] = 10;
        return result;
    }

    private void setTag(float[] tagResult, int numTags,
                        int tokenIndex, int tagIndex) {
        tagResult[tokenIndex * numTags + tagIndex] = 10;
    }

    public SpeechConfig testConfig() {
        return new SpeechConfig()
              .put("nlu-model-path", "model-path")
              .put("nlu-metadata-path", "src/test/resources/nlu.json")
              .put("nlu-input-length", 6);
    }

    public static class TestModel extends TensorflowModel {
        public TestModel(TensorflowModel.Loader loader) {
            super(loader);
        }

        public void run() {
            this.inputs(0).rewind();
            this.outputs(0).rewind();
            this.outputs(1).rewind();
        }

        public final void setOutputs(float[] intentsOut, float[] tagsOut) {
            this.outputs(0).rewind();
            for (float f : intentsOut) {
                this.outputs(0).putFloat(f);
            }
            this.outputs(1).rewind();
            for (float o : tagsOut) {
                this.outputs(1).putFloat(o);
            }
        }
    }

    public static class TestEnv implements TextEncoder {
        public final TensorflowModel.Loader loader;
        public final TensorflowNLUTest.TestModel testModel;
        public final TensorflowNLU nlu;
        public final Metadata metadata;

        public TestEnv(SpeechConfig config) throws Exception {
            // fetch configuration parameters
            int inputLength = config.getInteger("nlu-input-length");
            String metadataPath = config.getString("nlu-metadata-path");
            this.metadata = loadMetadata(metadataPath);

            // create/mock tensorflow-lite models
            this.loader = spy(TensorflowModel.Loader.class);
            this.testModel = mock(TensorflowNLUTest.TestModel.class);

            doReturn(ByteBuffer
                  .allocateDirect(inputLength * metadata.getIntents().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).inputs(0);
            doReturn(ByteBuffer
                  .allocateDirect(metadata.getIntents().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).outputs(0);
            doReturn(ByteBuffer
                  .allocateDirect(inputLength * metadata.getTags().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).outputs(1);
            doReturn(4).when(this.testModel).getInputSize();
            doCallRealMethod().when(this.testModel).run();
            doReturn(this.testModel)
                  .when(this.loader).load();

            this.nlu = new TensorflowNLU(config, this.loader, this);
        }

        private Metadata loadMetadata(String metadataPath)
              throws FileNotFoundException {
            FileReader fileReader = new FileReader(metadataPath);
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(fileReader);
            return gson.fromJson(reader, Metadata.class);
        }

        public Future<NLUResult> classify(String utterance) {
            return nlu.classify(utterance, new HashMap<>());
        }

        @Override
        public int encodeSingle(String token) {
            return 0;
        }

        @Override
        public EncodedTokens encode(String text) {
            if (text.equals("error")) {
                throw new IllegalStateException("forced test error");
            }
            String[] split = text.split(" ");
            EncodedTokens encoded = new EncodedTokens(split);
            List<Integer> ids = new ArrayList<>();
            List<Integer> originalIndices = new ArrayList<>();
            for (int i = 0; i < split.length; i++) {
                ids.add(0);
                originalIndices.add(i);
            }
            encoded.addTokenIds(ids);
            encoded.setOriginalIndices(originalIndices);
            return encoded;
        }
    }
}