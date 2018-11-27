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

public class WakewordTriggerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    public void testConstruction() throws Exception {
        final SpeechConfig config = new SpeechConfig();

        final TensorflowModel.Loader loader = spy(TensorflowModel.Loader.class);
        final TensorflowModel filterModel = mock(TestModel.class);
        final TensorflowModel detectModel = mock(TestModel.class);
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
    public void testProcessing() throws Exception {
        SpeechConfig config = new SpeechConfig()
            .put("sample-rate", 16000)
            .put("frame-width", 10)
            .put("fft-hop-length", 10)
            .put("fft-window-size", 160)
            .put("wake-words", "hello")
            .put("wake-filter-path", "filter-path")
            .put("wake-detect-path", "detect-path")
            .put("wake-smooth-length", 10)
            .put("wake-phrase-length", 20)
            .put("wake-active-min", 20)
            .put("wake-active-max", 30);

        TensorflowModel.Loader loader = spy(TensorflowModel.Loader.class);
        TensorflowModel filterModel = mock(TestModel.class);
        TensorflowModel detectModel = mock(TestModel.class);
        ByteBuffer frameBuffer = ByteBuffer.allocateDirect(160 * 4);
        doReturn(ByteBuffer
                .allocateDirect(81 * 4)
                .order(ByteOrder.nativeOrder()))
            .when(filterModel).inputs();
        doReturn(ByteBuffer
                .allocateDirect(40 * 4)
                .order(ByteOrder.nativeOrder()))
            .when(filterModel).outputs();
        doReturn(ByteBuffer
                .allocateDirect(40 * 40 * 4)
                .order(ByteOrder.nativeOrder()))
            .when(detectModel).inputs();
        doReturn(ByteBuffer
                .allocateDirect(2 * 4)
                .order(ByteOrder.nativeOrder()))
            .when(detectModel).outputs();
        doCallRealMethod().when(filterModel).run();
        doCallRealMethod().when(detectModel).run();
        doReturn(filterModel).doReturn(detectModel).when(loader).load();

        TestVad vad = spy(TestVad.class);
        WakewordTrigger wake = new WakewordTrigger(config, loader, vad);

        SpeechContext context = new SpeechContext();
        context.addOnSpeechEventListener(this);

        // inactive vad, inactive detector
        vad.active = false;
        wake.process(context, frameBuffer);
        verify(vad).process(any(SpeechContext.class), any(ByteBuffer.class));
        reset(vad);
        verify(filterModel, never()).run();
        verify(detectModel, never()).run();
        assertEquals(null, this.event);
        assertFalse(context.isActive());

        // active vad, inactive detector
        vad.active = true;
        wake.process(context, frameBuffer);
        verify(filterModel, atLeast(1)).run();
        verify(detectModel, atLeast(1)).run();
        assertEquals(null, this.event);
        assertFalse(context.isActive());

        // deactivated vad, inactive detector
        vad.active = false;
        wake.process(context, frameBuffer);
        assertEquals(null, this.event);
        assertFalse(context.isActive());

        // detector activation
        vad.active = true;

        detectModel.outputs().rewind();
        detectModel.outputs().putFloat(0);
        detectModel.outputs().putFloat(1);
        wake.process(context, frameBuffer);

        detectModel.outputs().rewind();
        detectModel.outputs().putFloat(1);
        detectModel.outputs().putFloat(0);
        wake.process(context, frameBuffer);

        assertEquals(SpeechContext.Event.ACTIVATE, this.event);
        assertTrue(context.isActive());

        // deactivated vad, activated detector
        wake.process(context, frameBuffer);

        vad.active = false;
        wake.process(context, frameBuffer);

        assertEquals(SpeechContext.Event.DEACTIVATE, this.event);
        assertFalse(context.isActive());

        // timeout, activated detector
        vad.active = true;

        detectModel.outputs().rewind();
        detectModel.outputs().putFloat(0);
        detectModel.outputs().putFloat(1);
        wake.process(context, frameBuffer);

        detectModel.outputs().rewind();
        detectModel.outputs().putFloat(1);
        detectModel.outputs().putFloat(0);
        wake.process(context, frameBuffer);

        wake.process(context, frameBuffer);
        wake.process(context, frameBuffer);
        wake.process(context, frameBuffer);

        assertEquals(SpeechContext.Event.DEACTIVATE, this.event);
        assertFalse(context.isActive());
    }

    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
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
    }
}
