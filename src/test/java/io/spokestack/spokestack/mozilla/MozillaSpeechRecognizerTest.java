package io.spokestack.spokestack.mozilla;

import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel;
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechStreamingState;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.constructor;
import static org.powermock.api.mockito.PowerMockito.suppress;

@RunWith(PowerMockRunner.class)
@SuppressStaticInitializationFor(
      "org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel")
public class MozillaSpeechRecognizerTest implements OnSpeechEventListener {

    private SpeechContext.Event event;

    @Before
    public void before() {
        suppress(constructor(MockModel.class));
    }

    @Test
    public void testRecognize() throws Exception {
        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);
        MockModel model = new MockModel("");
        MozillaSpeechRecognizer recognizer =
              new MozillaSpeechRecognizer(config, model);

        // inactive context
        recognizer.process(context, context.getBuffer().getLast());
        assertEquals(0, model.framesFed);
        assertNull(this.event);
        assertEquals("", context.getTranscript());

        // active context - sends all frames
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());
        assertEquals(3, model.framesFed);
        assertNotNull(this.event);
        assertEquals(SpeechContext.Event.ACTIVATE, this.event);
        assertEquals("", context.getTranscript());

        // send the next frame
        this.event = null;
        recognizer.process(context, context.getBuffer().getLast());
        assertEquals(4, model.framesFed);
        assertNull(this.event);
        assertEquals("", context.getTranscript());

        // end the request
        context.setActive(false);
        assertEquals(SpeechContext.Event.DEACTIVATE, this.event);
        recognizer.process(context, context.getBuffer().getLast());
        assertEquals(4, model.framesFed);
        assertNotNull(this.event);
        assertEquals(SpeechContext.Event.RECOGNIZE, this.event);
        assertEquals("success!", context.getTranscript());
    }

    private SpeechConfig createConfig() {
        SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        config.put("mozilla-model-path", "");
        return config;
    }

    private SpeechContext createContext(SpeechConfig config) {
        SpeechContext context = new SpeechContext(config);
        context.addOnSpeechEventListener(this);

        int sampleWidth = 2;
        int sampleRate = config.getInteger("sample-rate");
        int frameWidth = config.getInteger("frame-width");
        int frameSize = sampleRate * frameWidth / 1000 * sampleWidth;
        context.attachBuffer(new LinkedList<>());
        for (int i = 0; i < 3; i++) {
            context.getBuffer().addLast(ByteBuffer.allocateDirect(frameSize));
        }

        return context;
    }

    @Override
    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
    }

    private static class MockModel extends DeepSpeechModel {

        int framesFed = 0;

        public MockModel(String modelPath) {
            super(modelPath);
        }

        @Override
        public int setBeamWidth(long beamWidth) {
            return (int) beamWidth;
        }

        @Override
        public DeepSpeechStreamingState createStream() {
            return new DeepSpeechStreamingState(null);
        }

        @Override
        public void feedAudioContent(DeepSpeechStreamingState ctx,
                                     short[] buffer, int buffer_size) {
            framesFed++;
        }

        @Override
        public String finishStream(DeepSpeechStreamingState ctx) {
            return "success!";
        }
    }
}