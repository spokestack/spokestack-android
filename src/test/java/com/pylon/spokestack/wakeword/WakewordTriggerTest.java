package com.pylon.spokestack.wakeword;

import java.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.tensorflow.lite.Interpreter;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.Mockito.*;

import com.pylon.spokestack.OnSpeechEventListener;
import com.pylon.spokestack.SpeechConfig;
import com.pylon.spokestack.SpeechContext;
import com.pylon.spokestack.SpeechProcessor;
import com.pylon.spokestack.tensorflow.TensorflowModel;

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
            .put("wake-words", "hello")
            .put("wake-filter-path", "filter-path")
            .put("wake-detect-path", "detect-path");
        new WakewordTrigger(config, loader, new TestVad());

        // valid config
        config
            .put("wake-phrases", "hello")
            .put("wake-smooth-length", 300)
            .put("wake-phrase-length", 500)
            .put("wake-active-min", 500)
            .put("wake-active-max", 5000)
            .put("rms-target", 0.08)
            .put("rms-alpha", 0.1)
            .put("fft-window-size", 512)
            .put("fft-window-type", "hann")
            .put("fft-hop-length", 10)
            .put("mel-frame-length", 400)
            .put("mel-frame-width", 40);
        new WakewordTrigger(config, loader, new TestVad());

        // invalid wake phrases
        config.put("wake-phrases", "goodbye");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                new WakewordTrigger(config, loader, new TestVad());
            }
        });
        config.put("wake-phrases", "hello");

        // invalid fft window size
        config.put("fft-window-size", 513);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                new WakewordTrigger(config, loader, new TestVad());
            }
        });
        config.put("fft-window-size", 512);

        // invalid fft window type
        config.put("fft-window-type", "hamming");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() {
                new WakewordTrigger(config, loader, new TestVad());
            }
        });
        config.put("fft-window-type", "hann");

        // close coverage
        new WakewordTrigger(config, loader, new TestVad()).close();
    }

    @Test
    public void testDetInactiveVadInactive() throws Exception {
        // verify that filtering/detection models are not
        // run and activation doesn't occur when vad is inactive
        TestEnv env = new TestEnv(testConfig());

        env.vad.active = false;
        env.process();

        verify(env.vad).process(any(SpeechContext.class), any(ByteBuffer.class));
        verify(env.filter, never()).run();
        verify(env.detect, never()).run();
        reset(env.vad);

        assertEquals(null, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testDetInactiveVadActive() throws Exception {
        // verify that filtering/detection is run when vad is active
        // but that activation doesn't occur for the null class
        TestEnv env = new TestEnv(testConfig());

        env.vad.active = true;
        env.process();

        verify(env.filter, atLeast(1)).run();
        verify(env.detect, atLeast(1)).run();

        assertEquals(null, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testDetInactiveVadDeactivate() throws Exception {
        // verify that nothing happens when vad is deactivated
        // for the null class
        TestEnv env = new TestEnv(testConfig());

        env.vad.active = true;
        env.process();

        env.vad.active = false;
        env.process();

        assertEquals(null, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testDetActivate() throws Exception {
        // verify a simple activation - single keyword phrase
        TestEnv env = new TestEnv(testConfig());

        env.vad.active = true;
        env.detect.setOutputs(0, 1);
        env.process();

        env.detect.setOutputs(1, 0);
        env.process();

        assertEquals(SpeechContext.Event.ACTIVATE, env.event);
        assertTrue(env.context.isActive());
    }

    @Test
    public void testDetActiveVadDeactivate() throws Exception {
        // verify deactivation on vad timeout after min activation length
        TestEnv env = new TestEnv(testConfig());

        env.vad.active = true;
        env.detect.setOutputs(0, 1);
        env.process();

        env.detect.setOutputs(1, 0);
        env.process();

        env.process();
        env.process();
        env.process();

        assertEquals(SpeechContext.Event.DEACTIVATE, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testDetActiveMinDelay() throws Exception {
        // verify no deactivation on vad timeout before min activation length
        TestEnv env = new TestEnv(testConfig());

        env.vad.active = true;
        env.detect.setOutputs(0, 1);
        env.process();

        env.detect.setOutputs(1, 0);
        env.process();

        env.vad.active = false;
        env.process();
        env.process();

        assertTrue(env.context.isActive());
    }

    @Test
    public void testDetActiveTimeout() throws Exception {
        // verify max activation timeout
        TestEnv env = new TestEnv(testConfig());

        env.vad.active = true;
        env.detect.setOutputs(0, 1);
        env.process();

        env.detect.setOutputs(1, 0);
        env.process();
        env.process();

        env.vad.active = false;
        env.process();

        assertEquals(SpeechContext.Event.DEACTIVATE, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testDetManualVadDeactivate() throws Exception {
        // verify manual activation followed by vad deactivation
        TestEnv env = new TestEnv(testConfig());

        env.context.setActive(true);
        env.vad.active = true;
        env.process();
        env.process();

        env.vad.active = false;
        env.process();

        assertEquals(SpeechContext.Event.DEACTIVATE, env.event);
        assertFalse(env.context.isActive());
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
    public void testDetManualTimeout() throws Exception {
        // verify manual activation max activation timeout
        TestEnv env = new TestEnv(testConfig());

        env.context.setActive(true);
        env.process();
        env.process();
        env.process();
        env.process();

        assertEquals(SpeechContext.Event.DEACTIVATE, env.event);
        assertFalse(env.context.isActive());
    }

    @Test
    public void testMultiWord() throws Exception {
        // verify any word can trigger if no phrases configured
        TestEnv env = new TestEnv(testConfig()
            .put("wake-words", "up,dog"));

        env.vad.active = true;

        env.detect.setOutputs(0, 1, 0);
        env.process();
        env.detect.setOutputs(1, 0, 0);
        env.process();

        assertEquals(SpeechContext.Event.ACTIVATE, env.event);
        assertTrue(env.context.isActive());
        env.context.setActive(false);

        env.detect.setOutputs(0, 0, 1);
        env.process();
        env.detect.setOutputs(1, 0, 0);
        env.process();

        assertEquals(SpeechContext.Event.ACTIVATE, env.event);
        assertTrue(env.context.isActive());
    }

    @Test
    public void testExactPhrase() throws Exception {
        // verify only the specified phrase is matched if configured
        TestEnv env = new TestEnv(testConfig()
            .put("wake-words", "up,dog")
            .put("wake-phrases", "up dog")
            .put("wake-phrase-length", 30));

        env.vad.active = true;

        env.detect.setOutputs(0, 0, 1);
        env.process();
        env.detect.setOutputs(0, 1, 0);
        env.process();
        env.detect.setOutputs(1, 0, 0);
        env.process();

        assertFalse(env.context.isActive());

        env.detect.setOutputs(0, 1, 0);
        env.process();
        env.detect.setOutputs(0, 0, 1);
        env.process();
        env.detect.setOutputs(1, 0, 0);
        env.process();

        assertEquals(SpeechContext.Event.ACTIVATE, env.event);
        assertTrue(env.context.isActive());
    }

    @Test
    public void testWord1PauseWord2() throws Exception {
        // verify no activation if a vad deactivation occurs between keywords
        TestEnv env = new TestEnv(testConfig()
            .put("wake-words", "up,dog")
            .put("wake-phrases", "up dog")
            .put("wake-phrase-length", 40));

        env.vad.active = true;
        env.detect.setOutputs(0, 1, 0);
        env.process();

        env.vad.active = false;
        env.process();

        env.vad.active = true;
        env.detect.setOutputs(0, 0, 1);
        env.process();
        env.detect.setOutputs(1, 0, 0);
        env.process();

        assertFalse(env.context.isActive());
    }

    @Test
    public void testWord1WordsWord2() throws Exception {
        // verify no activation if intermediate words fill the phrase buffer
        TestEnv env = new TestEnv(testConfig()
            .put("wake-words", "up,dog,yo")
            .put("wake-phrases", "up dog")
            .put("wake-phrase-length", 30));

        env.vad.active = true;

        env.detect.setOutputs(0, 1, 0, 0);
        env.process();
        env.detect.setOutputs(0, 0, 0, 1);
        env.process();
        env.detect.setOutputs(0, 0, 1, 0);
        env.process();
        env.detect.setOutputs(1, 0, 0, 0);
        env.process();

        assertFalse(env.context.isActive());
    }

    @Test
    public void testSmoothing() throws Exception {
        // verify that detector posteriors are smoothed with moving average
        TestEnv env = new TestEnv(testConfig()
            .put("wake-smooth-length", 20));

        env.vad.active = true;

        env.detect.setOutputs(1, 0);
        env.process();
        env.detect.setOutputs(0, 0.5f);
        env.process();
        env.detect.setOutputs(1, 0);
        env.process();

        assertFalse(env.context.isActive());

        env.detect.setOutputs(0, 0.5f);
        env.process();
        env.detect.setOutputs(0, 0.5f);
        env.process();
        env.detect.setOutputs(1, 0);
        env.process();

        assertEquals(SpeechContext.Event.ACTIVATE, env.event);
        assertTrue(env.context.isActive());
    }

    public SpeechConfig testConfig() {
        return new SpeechConfig()
            .put("sample-rate", 16000)
            .put("frame-width", 10)
            .put("fft-hop-length", 10)
            .put("fft-window-size", 160)
            .put("mel-frame-length", 40)
            .put("mel-frame-width", 40)
            .put("wake-words", "hello")
            .put("wake-filter-path", "filter-path")
            .put("wake-detect-path", "detect-path")
            .put("wake-smooth-length", 10)
            .put("wake-phrase-length", 20)
            .put("wake-active-min", 20)
            .put("wake-active-max", 30);
    }

    public static class TestVad implements SpeechProcessor {
        public boolean active;

        public void close() {
        }

        public void process(SpeechContext context, ByteBuffer buffer) {
            context.setActive(this.active);
        }
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
        public final TestModel detect;
        public final ByteBuffer frame;
        public final TestVad vad;
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
            String[] words = config.getString("wake-words").split(",");

            // create/mock tensorflow-lite models
            this.loader = spy(TensorflowModel.Loader.class);
            this.filter = mock(TestModel.class);
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
                .when(this.detect).inputs();
            doReturn(ByteBuffer
                        .allocateDirect((words.length + 1) * 4)
                        .order(ByteOrder.nativeOrder()))
                .when(this.detect).outputs();
            doCallRealMethod().when(this.filter).run();
            doCallRealMethod().when(this.detect).run();
            doReturn(this.filter).doReturn(this.detect).when(this.loader).load();

            // create the frame buffer, vad mock, and wakeword trigger
            this.frame = ByteBuffer.allocateDirect(frameWidth * sampleRate / 1000 * 2);
            this.vad = spy(TestVad.class);
            this.wake = new WakewordTrigger(config, this.loader, this.vad);

            // create the speech context for processing calls
            this.context = new SpeechContext();
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
