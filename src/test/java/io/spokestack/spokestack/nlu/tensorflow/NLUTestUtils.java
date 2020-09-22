package io.spokestack.spokestack.nlu.tensorflow;

import android.os.SystemClock;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.tensorflow.TensorflowModel;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

public class NLUTestUtils {

    public static SpeechConfig testConfig() {
        return new SpeechConfig()
              .put("nlu-model-path", "model-path")
              .put("nlu-metadata-path", "src/test/resources/nlu.json")
              .put("wordpiece-vocab-path", "src/test/resources/vocab.txt");
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
        public final TestModel testModel;
        public final TensorflowNLU.Builder nluBuilder;
        public final Metadata metadata;

        public TensorflowNLU nlu;

        public TestEnv() throws Exception {
            this(testConfig());
        }

        public TestEnv(SpeechConfig config) throws Exception {
            // fetch configuration parameters
            String metadataPath = config.getString("nlu-metadata-path");
            this.metadata = loadMetadata(metadataPath);

            // create/mock tensorflow-lite models
            int maxTokens = 100;
            this.loader = spy(TensorflowModel.Loader.class);
            this.testModel = mock(TestModel.class);

            doReturn(ByteBuffer
                  .allocateDirect(maxTokens * metadata.getIntents().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).inputs(0);
            doReturn(ByteBuffer
                  .allocateDirect(metadata.getIntents().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).outputs(0);
            doReturn(ByteBuffer
                  .allocateDirect(maxTokens * metadata.getTags().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).outputs(1);
            doReturn(4).when(this.testModel).getInputSize();
            doCallRealMethod().when(this.testModel).run();
            doReturn(this.testModel)
                  .when(this.loader).load();

            this.nluBuilder =
                  new TensorflowNLU.Builder()
                        .setConfig(config)
                        .setModelLoader(this.loader)
                        .setTextEncoder(this);
        }

        private Metadata loadMetadata(String metadataPath)
              throws FileNotFoundException {
            FileReader fileReader = new FileReader(metadataPath);
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(fileReader);
            return gson.fromJson(reader, Metadata.class);
        }

        public Future<NLUResult> classify(String utterance) {
            if (this.nlu == null) {
                this.nlu = this.nluBuilder.build();
            }
            return this.nlu.classify(utterance);
        }

        @Override
        public int encodeSingle(String token) {
            return 1;
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
