package io.spokestack.spokestack.tts;

import android.content.Context;
import android.net.Uri;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechOutput;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.fail;

public class TTSTestUtils {

    public static class Service extends TTSService {

        public Service(SpeechConfig config) {
            String key = config.getString("spokestack-id", "default");
            if (!key.equals("default")) {
                fail("custom client ID should not be set by tests");
            }
        }

        @Override
        public void synthesize(SynthesisRequest request) {
            TTSEvent synthesisComplete =
                  new TTSEvent(TTSEvent.Type.AUDIO_AVAILABLE);
            AudioResponse response = new AudioResponse(Uri.EMPTY);
            synthesisComplete.setTtsResponse(response);
            dispatch(synthesisComplete);
        }

        @Override
        public void close() {
        }
    }

    public static class Output extends SpeechOutput {

        LinkedBlockingQueue<String> events;

        @SuppressWarnings("unused")
        public Output(SpeechConfig config) {
            this.events = new LinkedBlockingQueue<>();
        }

        public void setEventQueue(LinkedBlockingQueue<String> events) {
            this.events = events;
        }

        public LinkedBlockingQueue<String> getEvents() {
            return events;
        }

        @Override
        public void audioReceived(AudioResponse response) {
            this.events.add("audioReceived");
        }

        @Override
        public void stopPlayback() {
            this.events.add("stop");
        }

        @Override
        public void setAndroidContext(Context appContext) {
        }

        @Override
        public void close() {
            throw new RuntimeException("can't close won't close");
        }
    }
}
