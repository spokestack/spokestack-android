package io.spokestack.spokestack.wakeword;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.tensorflow.TensorflowModel;

public class WakewordTriggerTest {
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
            .put("wake-filter-path", "filter-path")
            .put("wake-encode-path", "encode-path")
            .put("wake-detect-path", "detect-path");
        new WakewordTrigger(config, loader);

        // valid config
        config
            .put("wake-active-min", 500)
            .put("wake-active-max", 5000)
            .put("rms-target", 0.08)
            .put("rms-alpha", 0.1)
            .put("fft-window-size", 512)
            .put("fft-window-type", "hann")
            .put("fft-hop-length", 10)
            .put("mel-frame-length", 400)
            .put("mel-frame-width", 40);
        new WakewordTrigger(config, loader);

        // invalid fft window size
        config.put("fft-window-size", 513);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                new WakewordTrigger(config, loader);
            }
        });
        config.put("fft-window-size", 512);

        // invalid fft window type
        config.put("fft-window-type", "hamming");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                new WakewordTrigger(config, loader);
            }
        });
        config.put("fft-window-type", "hann");

        // close coverage
        new WakewordTrigger(config, loader).close();
    }

    @Test
    public void testDetInactiveVadInactive() throws Exception {
        // verify that filtering/detection models are not
        // run and activation doesn't occur when vad is inactive
        TestEnv env = new TestEnv(testConfig());

        env.context.setSpeech(false);
        env.process();

        verify(env.filter, never()).run();
        verify(env.encode, never()).run();
        verify(env.detect, never()).run();

        assertEquals(null, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testDetInactiveVadActive() throws Exception {
        // verify that filtering/detection is run when vad is active
        // but that activation doesn't occur for the null class
        TestEnv env = new TestEnv(testConfig());

        env.context.setSpeech(true);
        env.process();

        verify(env.filter, atLeast(1)).run();
        verify(env.encode, atLeast(1)).run();
        verify(env.detect, atLeast(1)).run();

        assertEquals(null, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testDetInactiveVadDeactivate() throws Exception {
        // verify that nothing happens when vad is deactivated
        // for the null class
        TestEnv env = new TestEnv(testConfig());

        env.context.setSpeech(true);
        env.process();

        env.context.setSpeech(false);
        env.process();

        assertEquals(null, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testDetActivate() throws Exception {
        // verify a simple activation - single keyword phrase
        TestEnv env = new TestEnv(testConfig());

        env.context.setSpeech(true);
        env.detect.setOutputs(0);
        env.process();

        env.detect.setOutputs(1);
        env.process();

        assertEquals(SpeechContext.Event.ACTIVATE, env.event);
        assertTrue(env.context.isActive());
    }

    @Test
    public void testDetActiveMinDelay() throws Exception {
        // verify no deactivation on vad timeout before min activation length
        TestEnv env = new TestEnv(testConfig());

        env.context.setSpeech(true);
        env.detect.setOutputs(0);
        env.process();

        env.detect.setOutputs(1);
        env.process();

        env.context.setSpeech(false);
        env.process();
        env.process();

        assertTrue(env.context.isActive());
    }

    @Test
    public void testDetManualMinDelay() throws Exception {
        // verify manual activation remains with no vad activation/deactivation
        TestEnv env = new TestEnv(testConfig());

        env.context.setActive(true);
        env.process();
        env.process();
        env.process();

        assertTrue(env.context.isActive());
    }

    @Test
    public void testTracing() throws Exception {
        // exercise trace events on activation
        TestEnv env = new TestEnv(testConfig()
            .put("trace-level", 0));

        env.context.setSpeech(true);
        env.detect.setOutputs(0);
        env.process();

        env.detect.setOutputs(1);
        env.process();

        assertTrue(env.context.getMessage() != null);
    }

    public SpeechConfig testConfig() {
        return new SpeechConfig()
            .put("sample-rate", 16000)
            .put("frame-width", 10)
            .put("rms-alpha", 0.1)
            .put("pre-emphasis", 0.97)
            .put("fft-hop-length", 10)
            .put("fft-window-size", 160)
            .put("mel-frame-length", 40)
            .put("mel-frame-width", 40)
            .put("wake-filter-path", "filter-path")
            .put("wake-encode-path", "encode-path")
            .put("wake-detect-path", "detect-path")
            .put("wake-encode-length", 1000)
            .put("wake-encode-width", 128);
    }

    public static class TestModel extends TensorflowModel {
        public TestModel(TensorflowModel.Loader loader) {
            super(loader);
        }

        public void run() {
            this.inputs().rewind();
            this.outputs().rewind();
        }

        public final void setOutputs(float ...outputs) {
            this.outputs().rewind();
            for (float o: outputs)
                this.outputs().putFloat(o);
        }
    }

    public class TestEnv implements OnSpeechEventListener {
        public final TensorflowModel.Loader loader;
        public final TestModel filter;
        public final TestModel encode;
        public final TestModel detect;
        public final ByteBuffer frame;
        public final WakewordTrigger wake;
        public final SpeechContext context;
        public SpeechContext.Event event;

        public TestEnv(SpeechConfig config) {
            // fetch configuration parameters
            int sampleRate = config.getInteger("sample-rate");
            int frameWidth = config.getInteger("frame-width");
            int windowSize = config.getInteger("fft-window-size");
            int fftSize = windowSize / 2 + 1;
            int hopLength = config.getInteger("fft-hop-length");
            int melLength = config.getInteger("mel-frame-length") * sampleRate / 1000 / hopLength;
            int melWidth = config.getInteger("mel-frame-width");
            int encodeLength = config.getInteger("wake-encode-length") * sampleRate / 1000 / hopLength;
            int encodeWidth = config.getInteger("wake-encode-width");

            // create/mock tensorflow-lite models
            this.loader = spy(TensorflowModel.Loader.class);
            this.filter = mock(TestModel.class);
            this.encode = mock(TestModel.class);
            this.detect = mock(TestModel.class);

            doReturn(ByteBuffer
                        .allocateDirect(fftSize * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.filter).inputs();
            doReturn(ByteBuffer
                        .allocateDirect(melWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.filter).outputs();
            doReturn(ByteBuffer
                        .allocateDirect(melLength * melWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.encode).inputs();
            doReturn(ByteBuffer
                        .allocateDirect(encodeWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.encode).states();
            doReturn(ByteBuffer
                        .allocateDirect(encodeWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.encode).outputs();
            doReturn(ByteBuffer
                        .allocateDirect(encodeLength * encodeWidth * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.detect).inputs();
            doReturn(ByteBuffer
                        .allocateDirect(1 * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.detect).outputs();
            doCallRealMethod().when(this.filter).run();
            doCallRealMethod().when(this.encode).run();
            doCallRealMethod().when(this.detect).run();
            doReturn(this.filter)
                .doReturn(this.encode)
                .doReturn(this.detect)
                .when(this.loader).load();

            // create the frame buffer and wakeword trigger
            this.frame = ByteBuffer.allocateDirect(frameWidth * sampleRate / 1000 * 2);
            this.wake = new WakewordTrigger(config, this.loader);

            // create the speech context for processing calls
            this.context = new SpeechContext(config);
            context.addOnSpeechEventListener(this);
        }

        public void process() throws Exception {
            this.wake.process(this.context, this.frame);
        }

        public void onEvent(SpeechContext.Event event, SpeechContext context) {
            this.event = event;
        }
    }
}
