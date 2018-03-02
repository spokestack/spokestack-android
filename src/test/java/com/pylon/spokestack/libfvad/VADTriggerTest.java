import java.util.*;
import java.nio.ByteBuffer;

import org.junit.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.pylon.spokestack.SpeechContext;
import com.pylon.spokestack.libfvad.VADTrigger;

public class VADTriggerTest implements SpeechContext.OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    public void testConstruction() {
        // default config
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        new VADTrigger(config);

        // invalid mode
        config.put("vad-mode", "invalid");
        invalidConstruct(config);

        // invalid sample rate
        config.put("sample-rate", 44100);
        config.put("frame-width", 20);
        invalidConstruct(config);

        // invalid frame width
        config.put("sample-rate", 8000);
        config.put("frame-width", 25);
        invalidConstruct(config);

        // valid config
        config.put("vad-mode", "quality");
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        new VADTrigger(config);

        // valid rates
        config.put("sample-rate", 8000);
        new VADTrigger(config);
        config.put("sample-rate", 16000);
        new VADTrigger(config);
        config.put("sample-rate", 32000);
        new VADTrigger(config);
        config.put("sample-rate", 48000);
        new VADTrigger(config);

        // valid widths
        config.put("frame-width", 10);
        new VADTrigger(config);
        config.put("frame-width", 20);
        new VADTrigger(config);
        config.put("frame-width", 30);
        new VADTrigger(config);

        // valid modes
        config.put("vad-mode", "quality");
        new VADTrigger(config);
        config.put("vad-mode", "low-bitrate");
        new VADTrigger(config);
        config.put("vad-mode", "aggressive");
        new VADTrigger(config);
        config.put("vad-mode", "very-aggressive");
        new VADTrigger(config);

        // finalize coverage
        System.gc();
        System.runFinalization();
    }

    private void invalidConstruct(Map<String, Object> config) {
        try {
            new VADTrigger(config);
            fail("expected: exception");
        } catch (IllegalArgumentException e) { }
    }

    @Test
    public void testProcessing() {
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        config.put("vad-mode", "quality");
        config.put("vad-rise-delay", 30);
        config.put("vad-fall-delay", 40);

        SpeechContext context = new SpeechContext();
        context.addOnSpeechEventListener(this);

        VADTrigger vad = new VADTrigger(config);

        // invalid frame
        try {
            vad.process(context, ByteBuffer.allocateDirect(1));
            fail("expected: exception");
        } catch (IllegalStateException e) {
            assertEquals(null, this.event);
        }

        // initial silence
        for (int i = 0; i < 10; i++) {
            vad.process(context, silenceFrame(config));
            assertEquals(null, this.event);
            assertFalse(context.isActive());
        }

        // voice transition
        this.event = null;
        for (int i = 0; i < 2; i++) {
            vad.process(context, voiceFrame(config));
            assertEquals(null, this.event);
            assertFalse(context.isActive());
        }
        vad.process(context, voiceFrame(config));
        assertEquals(SpeechContext.Event.ACTIVATE, this.event);
        assertTrue(context.isActive());

        // silence transition
        this.event = null;
        int filterLag = 8;
        for (int i = 0; i < filterLag + 4; i++) {
            vad.process(context, silenceFrame(config));
            assertEquals(null, this.event);
            assertTrue(context.isActive());
        }
        vad.process(context, silenceFrame(config));
        assertEquals(SpeechContext.Event.DEACTIVATE, this.event);
        assertFalse(context.isActive());
    }

    private ByteBuffer silenceFrame(Map<String, Object> config) {
        ByteBuffer buffer = sampleBuffer(config);
        for (int i = 0; i < buffer.capacity() / 2; i++)
            buffer.putShort(i, (short)0);
        return buffer;
    }

    private ByteBuffer voiceFrame(Map<String, Object> config) {
        ByteBuffer buffer = sampleBuffer(config);
        double rate = (Integer)config.get("sample-rate");
        double freq = 2000;
        for (int i = 0; i < buffer.capacity() / 2; i++) {
            double sample = Math.sin(i / (rate / freq) * 2 * Math.PI);
            buffer.putShort(i, (short)(sample * Short.MAX_VALUE));
        }
        return buffer;
    }

    private ByteBuffer sampleBuffer(Map<String, Object> config) {
        int samples = (Integer)config.get("sample-rate")
            / 1000
            * (Integer)config.get("frame-width");
        return ByteBuffer.allocateDirect(samples * 2);
    }


    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
    }
}
