import java.nio.ByteBuffer;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.webrtc.VoiceActivityTrigger;

public class VoiceActivityTriggerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    public void testConstruction() {
        // default config
        final SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);
        new VoiceActivityTrigger(config);

        // close coverage
        new VoiceActivityTrigger(config).close();
    }

    @Test
    public void testProcessing() {
        final SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 8000);
        config.put("frame-width", 10);

        final SpeechContext context = new SpeechContext(config);
        final VoiceActivityTrigger vad = new VoiceActivityTrigger(config);
        context.addOnSpeechEventListener(this);

        // initial silence
        vad.process(context, sampleBuffer(config));
        assertEquals(null, this.event);
        assertFalse(context.isActive());

        // speech transition
        context.setSpeech(true);
        vad.process(context, sampleBuffer(config));
        assertEquals(SpeechContext.Event.ACTIVATE, this.event);
        assertTrue(context.isActive());

        // continued speech
        this.event = null;
        vad.process(context, sampleBuffer(config));
        assertEquals(null, this.event);
        assertTrue(context.isActive());

        // silence transition
        context.setSpeech(false);
        vad.process(context, sampleBuffer(config));
        assertEquals(SpeechContext.Event.DEACTIVATE, this.event);
        assertFalse(context.isActive());

        // continued silence
        this.event = null;
        vad.process(context, sampleBuffer(config));
        assertEquals(null, this.event);
        assertFalse(context.isActive());
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
