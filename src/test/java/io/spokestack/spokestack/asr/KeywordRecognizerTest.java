package io.spokestack.spokestack.asr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import androidx.annotation.NonNull;
import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.tensorflow.TensorflowModel;

public class KeywordRecognizerTest {
    @Test
    public void testConstruction() throws Exception {
        final SpeechConfig config = new SpeechConfig();

        final TensorflowModel.Loader loader = spy(TensorflowModel.Loader.class);
        final TestModel filterModel = mock(TestModel.class);
        final TestModel detectModel = mock(TestModel.class);
        doReturn(filterModel).doReturn(detectModel).when(loader).load();

        // default config
        config
            .put("sample-rate", 16000)
            .put("frame-width", 10)
            .put("keyword-classes", "up,dog")
            .put("keyword-filter-path", "filter-path")
            .put("keyword-encode-path", "encode-path")
            .put("keyword-detect-path", "detect-path");
        new KeywordRecognizer(config, loader);

        // valid config
        config
            .put("keyword-pre-emphasis", 0.9)
            .put("keyword-fft-window-size", 512)
            .put("keyword-fft-window-type", "hann")
            .put("keyword-fft-hop-length", 10)
            .put("keyword-mel-frame-length", 400)
            .put("keyword-mel-frame-width", 40);
        new KeywordRecognizer(config, loader);

        // invalid classes
        config.put("keyword-classes", ",");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                new KeywordRecognizer(config, loader);
            }
        });
        config.put("keyword-classes", "up,dog");

        // invalid fft window size
        config.put("keyword-fft-window-size", 513);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                new KeywordRecognizer(config, loader);
            }
        });
        config.put("keyword-fft-window-size", 512);

        // invalid fft window type
        config.put("keyword-fft-window-type", "hamming");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                new KeywordRecognizer(config, loader);
            }
        });
        config.put("keyword-fft-window-type", "hann");

        // close coverage
        new KeywordRecognizer(config, loader).close();
    }

    @Test
    public void testRecSilentCtxInactive() throws Exception {
        // verify that filtering/detection models are not
        // run and recognition doesn't occur when the context is inactive
        TestEnv env = new TestEnv(testConfig());

        env.context.setActive(false);
        env.process();

        verify(env.filter, never()).run();
        verify(env.encode, never()).run();
        verify(env.detect, never()).run();

        assertEquals(null, env.event);
    }

    @Test
    public void testRecSilentCtxActive() throws Exception {
        // verify that filtering/encoding is run when the context is active
        // but that detection/recognition doesn't occur
        TestEnv env = new TestEnv(testConfig());

        env.context.setActive(true);
        env.event = null;
        env.process();

        verify(env.filter, atLeast(1)).run();
        verify(env.encode, atLeast(1)).run();
        verify(env.detect, never()).run();

        assertEquals(null, env.event);
    }

    @Test
    public void testRecNullCtxDeactivate() throws Exception {
        // verify that a timeout is raised for the null class
        TestEnv env = new TestEnv(testConfig());

        env.context.setActive(true);
        env.process();

        env.context.setActive(false);
        env.process();

        assertEquals(SpeechContext.Event.TIMEOUT, env.event);
        assertEquals("", env.context.getTranscript());
    }

    @Test
    public void testRecValidCtxDeactivate() throws Exception {
        // verify that a valid transcript is raised for a valid class
        TestEnv env = new TestEnv(testConfig());

        env.context.setActive(true);
        env.detect.setOutputs(0.5f, 0.9f);
        env.process();

        env.context.setActive(false);
        env.process();

        assertEquals(SpeechContext.Event.RECOGNIZE, env.event);
        assertEquals("dog", env.context.getTranscript());
        assertEquals(0.9f, env.context.getConfidence());
    }

    @Test
    public void testTracing() throws Exception {
        // exercise trace events on deactivation/recognition
        TestEnv env = new TestEnv(testConfig()
            .put("trace-level", 0));

        env.context.setActive(true);
        env.process();

        env.context.setActive(false);
        env.process();

        assertTrue(env.context.getMessage() != null);
    }


    public SpeechConfig testConfig() {
        return new SpeechConfig()
            .put("sample-rate", 16000)
            .put("frame-width", 10)
            .put("keyword-classes", "up,dog")
            .put("keyword-pre-emphasis", 0.97)
            .put("keyword-fft-hop-length", 10)
            .put("keyword-fft-window-size", 160)
            .put("keyword-mel-frame-length", 40)
            .put("keyword-mel-frame-width", 40)
            .put("keyword-filter-path", "filter-path")
            .put("keyword-encode-path", "encode-path")
            .put("keyword-detect-path", "detect-path")
            .put("keyword-encode-length", 1000)
            .put("keyword-encode-width", 128);
    }

    public static class TestModel extends TensorflowModel {
        public TestModel(TensorflowModel.Loader loader) {
            super(loader);
        }

        public void run() {
            this.inputs(0).rewind();
            this.outputs(0).rewind();
        }

        public final void setOutputs(float ...outputs) {
            this.outputs(0).rewind();
            for (float o: outputs)
                this.outputs(0).putFloat(o);
        }
    }

    public class TestEnv implements OnSpeechEventListener {
        public final TensorflowModel.Loader loader;
        public final TestModel filter;
        public final TestModel encode;
        public final TestModel detect;
        public final ByteBuffer frame;
        public final KeywordRecognizer recognizer;
        public final SpeechContext context;
        public SpeechContext.Event event;

        public TestEnv(SpeechConfig config) {
            // fetch configuration parameters
            int sampleRate = config.getInteger("sample-rate");
            int frameWidth = config.getInteger("frame-width");
            int windowSize = config.getInteger("keyword-fft-window-size");
            int fftSize = windowSize / 2 + 1;
            int hopLength = config.getInteger("keyword-fft-hop-length");
            int melLength = config.getInteger("keyword-mel-frame-length") * sampleRate / 1000 / hopLength;
            int melWidth = config.getInteger("keyword-mel-frame-width");
            int encodeLength = config.getInteger("keyword-encode-length") * sampleRate / 1000 / hopLength;
            int encodeWidth = config.getInteger("keyword-encode-width");

            // create/mock tensorflow-lite models
            this.loader = spy(TensorflowModel.Loader.class);
            this.filter = mock(TestModel.class);
            this.encode = mock(TestModel.class);
            this.detect = mock(TestModel.class);

            doReturn(ByteBuffer
                        .allocateDirect(fftSize * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.filter).inputs(0);
            doReturn(ByteBuffer
                        .allocateDirect(melWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.filter).outputs(0);
            doReturn(ByteBuffer
                        .allocateDirect(melLength * melWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.encode).inputs(0);
            doReturn(ByteBuffer
                        .allocateDirect(encodeWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.encode).states();
            doReturn(ByteBuffer
                        .allocateDirect(encodeWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.encode).outputs(0);
            doReturn(ByteBuffer
                        .allocateDirect(encodeLength * encodeWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.detect).inputs(0);
            doReturn(ByteBuffer
                        .allocateDirect(2 * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.detect).outputs(0);
            doCallRealMethod().when(this.filter).run();
            doCallRealMethod().when(this.encode).run();
            doCallRealMethod().when(this.detect).run();
            doReturn(this.filter)
                .doReturn(this.encode)
                .doReturn(this.detect)
                .when(this.loader).load();

            // create the frame buffer and keyword recognizer
            this.frame = ByteBuffer.allocateDirect(frameWidth * sampleRate / 1000 * 2);
            this.recognizer = new KeywordRecognizer(config, this.loader);

            // create the speech context for processing calls
            this.context = new SpeechContext(config);
            context.addOnSpeechEventListener(this);
        }

        public void process() throws Exception {
            this.recognizer.process(this.context, this.frame);
        }

        public void onEvent(@NonNull SpeechContext.Event event,
                            @NonNull SpeechContext context) {
            this.event = event;
        }
    }
}
