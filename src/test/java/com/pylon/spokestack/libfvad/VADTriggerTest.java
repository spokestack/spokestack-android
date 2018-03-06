import java.util.*;
import java.nio.ByteBuffer;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import com.pylon.spokestack.SpeechConfig;
import com.pylon.spokestack.SpeechContext;
import com.pylon.spokestack.OnSpeechEventListener;
import com.pylon.spokestack.libfvad.VADTrigger;

public class VADTriggerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    public void testConstruction() {
        // default config
        final SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        new VADTrigger(config);

        // invalid mode
        config.put("vad-mode", "invalid");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new VADTrigger(config); }
        });

        // invalid sample rate
        config.put("sample-rate", 44100);
        config.put("frame-width", 20);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new VADTrigger(config); }
        });

        // invalid frame width
        config.put("sample-rate", 8000);
        config.put("frame-width", 25);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new VADTrigger(config); }
        });

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

        // close coverage
        new VADTrigger(config).close();
    }

    @Test
    public void testProcessing() {
        final SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        config.put("vad-mode", "quality");
        config.put("vad-rise-delay", 30);
        config.put("vad-fall-delay", 40);

        final SpeechContext context = new SpeechContext();
        context.addOnSpeechEventListener(this);

        final VADTrigger vad = new VADTrigger(config);

        // invalid frame
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() {
                vad.process(context, ByteBuffer.allocateDirect(1));
            }
        });
        assertEquals(null, this.event);

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

    private ByteBuffer silenceFrame(SpeechConfig config) {
        ByteBuffer buffer = sampleBuffer(config);
        for (int i = 0; i < buffer.capacity() / 2; i++)
            buffer.putShort(i, (short)0);
        return buffer;
    }

    private ByteBuffer voiceFrame(SpeechConfig config) {
        ByteBuffer buffer = sampleBuffer(config);
        double rate = config.getInteger("sample-rate");
        double freq = 2000;
        for (int i = 0; i < buffer.capacity() / 2; i++) {
            double sample = Math.sin(i / (rate / freq) * 2 * Math.PI);
            buffer.putShort(i, (short)(sample * Short.MAX_VALUE));
        }
        return buffer;
    }

    private ByteBuffer sampleBuffer(SpeechConfig config) {
        int samples = config.getInteger("sample-rate")
            / 1000
            * config.getInteger("frame-width");
        return ByteBuffer.allocateDirect(samples * 2);
    }

    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
    }
}
