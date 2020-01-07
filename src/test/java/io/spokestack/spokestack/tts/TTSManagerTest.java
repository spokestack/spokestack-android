package io.spokestack.spokestack.tts;


import android.content.Context;
import android.net.Uri;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechOutput;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class TTSManagerTest implements TTSListener {

    @Mock
    private Context context = mock(Context.class);

    private TTSEvent lastEvent;
    private static LinkedBlockingQueue<String> events;

    @Before
    public void before() {
        lastEvent = null;
        events = new LinkedBlockingQueue<>();
    }

    @Test
    public void testBuilder() throws Exception {
        // invalid TTS service
        assertThrows(ClassNotFoundException.class,
              () -> new TTSManager.Builder(context)
                    .setTTSServiceClass("invalid")
                    .build());

        // invalid output component
        assertThrows(ClassNotFoundException.class,
              () -> new TTSManager.Builder(context)
                    .setTTSServiceClass("io.spokestack.spokestack.tts.SpokestackTTSService")
                    .setProperty("spokestack-key", "test")
                    .setOutputClass("invalid")
                    .build());

        // valid config
        LifecycleRegistry lifecycleRegistry =
              new LifecycleRegistry(mock(LifecycleOwner.class));

        TTSManager manager = new TTSManager.Builder(context)
              .setTTSServiceClass("io.spokestack.spokestack.tts.TTSManagerTest$Input")
              .setOutputClass("io.spokestack.spokestack.tts.TTSManagerTest$Output")
              .setLifecycle(lifecycleRegistry)
              .addTTSListener(this)
              .build();

        assertThrows(IllegalStateException.class, manager::prepare);
        assertNotNull(manager.getTtsService());
        assertNotNull(manager.getOutput());

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        String event = events.poll(1, TimeUnit.SECONDS);
        assertEquals("onResume", event, "onResume not called");

        // synthesis
        SynthesisRequest request = new SynthesisRequest.Builder("test")
              .withVoice("voice-2")
              .withData(Collections.singletonMap("key", "value"))
              .build();
        manager.synthesize(request);
        event = events.poll(1, TimeUnit.SECONDS);
        assertEquals("audioReceived", event, "audioReceived not called");
        assertEquals(TTSEvent.Type.AUDIO_AVAILABLE, lastEvent.type);
        assertEquals(Uri.EMPTY, lastEvent.getTtsResponse().getAudioUri());

        manager.close();
        assertNull(manager.getTtsService());
        assertNull(manager.getOutput());

        // re-preparation is allowed
        manager.prepare();
        assertNotNull(manager.getTtsService());
        assertNotNull(manager.getOutput());

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        event = events.poll(1, TimeUnit.SECONDS);
        assertEquals("onResume", event, "onResume not called");

        String errorMsg = "can't close won't close";
        manager.close();
        assertEquals(TTSEvent.Type.ERROR, lastEvent.type);
        assertEquals(errorMsg, lastEvent.getError().getMessage());
    }

    @Override
    public void eventReceived(TTSEvent event) {
        lastEvent = event;
    }

    public static class Input extends TTSService {

        public Input(SpeechConfig config) {
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

    public static class Output extends SpeechOutput
          implements DefaultLifecycleObserver {

        public Output(SpeechConfig config) {
        }

        @Override
        public void onResume(@NotNull LifecycleOwner owner) {
            // protect against multiple calls by the registry during tests
            if (events.isEmpty()) {
                events.add("onResume");
            }
        }

        @Override
        public void audioReceived(AudioResponse response) {
            events.add("audioReceived");
        }

        @Override
        public void setAppContext(Context appContext) {
        }

        @Override
        public void close() {
            throw new RuntimeException("can't close won't close");
        }
    }
}