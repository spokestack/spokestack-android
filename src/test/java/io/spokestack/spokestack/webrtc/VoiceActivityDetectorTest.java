import java.nio.ByteBuffer;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.webrtc.VoiceActivityDetector;

public class VoiceActivityDetectorTest {

    @Test
    public void testConstruction() {
        // default config
        final SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        new VoiceActivityDetector(config);

        // invalid mode
        config.put("vad-mode", "invalid");
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new VoiceActivityDetector(config); }
        });

        // invalid sample rate
        config.put("sample-rate", 44100);
        config.put("frame-width", 20);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new VoiceActivityDetector(config); }
        });

        // invalid frame width
        config.put("sample-rate", 8000);
        config.put("frame-width", 25);
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { new VoiceActivityDetector(config); }
        });

        // valid config
        config.put("vad-mode", "quality");
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        new VoiceActivityDetector(config);

        // valid rates
        config.put("sample-rate", 8000);
        new VoiceActivityDetector(config);
        config.put("sample-rate", 16000);
        new VoiceActivityDetector(config);
        config.put("sample-rate", 32000);
        new VoiceActivityDetector(config);
        config.put("sample-rate", 48000);
        new VoiceActivityDetector(config);

        // valid widths
        config.put("frame-width", 10);
        new VoiceActivityDetector(config);
        config.put("frame-width", 20);
        new VoiceActivityDetector(config);
        config.put("frame-width", 30);
        new VoiceActivityDetector(config);

        // valid modes
        config.put("vad-mode", "quality");
        new VoiceActivityDetector(config);
        config.put("vad-mode", "low-bitrate");
        new VoiceActivityDetector(config);
        config.put("vad-mode", "aggressive");
        new VoiceActivityDetector(config);
        config.put("vad-mode", "very-aggressive");
        new VoiceActivityDetector(config);

        // close coverage
        new VoiceActivityDetector(config).close();
    }

    @Test
    public void testProcessing() {
        final SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        config.put("vad-mode", "quality");
        config.put("vad-rise-delay", 30);
        config.put("vad-fall-delay", 40);

        final SpeechContext context = new SpeechContext(config);
        final VoiceActivityDetector vad = new VoiceActivityDetector(config);

        // invalid frame
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() {
                vad.process(context, ByteBuffer.allocateDirect(1));
            }
        });

        // initial silence
        for (int i = 0; i < 10; i++) {
            vad.process(context, silenceFrame(config));
            assertFalse(context.isSpeech());
        }

        // voice transition
        for (int i = 0; i < 2; i++) {
            vad.process(context, voiceFrame(config));
            assertFalse(context.isSpeech());
        }
        vad.process(context, voiceFrame(config));
        assertTrue(context.isSpeech());

        // silence transition
        int filterLag = 8;
        for (int i = 0; i < filterLag + 4; i++) {
            vad.process(context, silenceFrame(config));
            assertTrue(context.isSpeech());
        }
        vad.process(context, silenceFrame(config));
        assertFalse(context.isSpeech());
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
}
