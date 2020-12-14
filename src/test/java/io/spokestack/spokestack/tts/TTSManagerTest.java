package io.spokestack.spokestack.tts;


import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import io.spokestack.spokestack.SpeechConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class TTSManagerTest implements TTSListener {

    @Mock
    private final Context context = mock(Context.class);

    private TTSEvent lastEvent;

    @Before
    public void before() {
        lastEvent = null;
    }

    @Test
    public void testBuilder() throws Exception {
        // invalid TTS service
        assertThrows(ClassNotFoundException.class,
              () -> new TTSManager.Builder()
                    .setTTSServiceClass("invalid")
                    .build());

        // invalid output component
        assertThrows(ClassNotFoundException.class,
              () -> new TTSManager.Builder()
                    .setTTSServiceClass("io.spokestack.spokestack.tts.SpokestackTTSService")
                    .setProperty("spokestack-id", "test")
                    .setProperty("spokestack-secret", "test")
                    .setOutputClass("invalid")
                    .build());

        TTSManager manager = new TTSManager.Builder()
              .setTTSServiceClass("io.spokestack.spokestack.tts.TTSTestUtils$Service")
              .setOutputClass("io.spokestack.spokestack.tts.TTSTestUtils$Output")
              .setProperty("spokestack-id", "test")
              .setConfig(new SpeechConfig())
              .setAndroidContext(context)
              .addTTSListener(this)
              .build();

        LinkedBlockingQueue<String> events =
              ((TTSTestUtils.Output) manager.getOutput()).getEvents();

        manager.prepare();
        assertNotNull(manager.getTtsService());
        assertNotNull(manager.getOutput());

        // synthesis
        SynthesisRequest request = new SynthesisRequest.Builder("test")
              .withVoice("voice-2")
              .withData(Collections.singletonMap("key", "value"))
              .build();
        manager.synthesize(request);
        String event = events.poll(1, TimeUnit.SECONDS);
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

        String errorMsg = "can't close won't close";
        manager.close();
        assertEquals(TTSEvent.Type.ERROR, lastEvent.type);
        assertEquals(errorMsg, lastEvent.getError().getMessage());
    }

    @Test
    public void testStop() throws Exception {
        TTSManager manager = new TTSManager.Builder()
              .setTTSServiceClass("io.spokestack.spokestack.tts.TTSTestUtils$Service")
              .setProperty("spokestack-id", "test")
              .setConfig(new SpeechConfig())
              .setAndroidContext(context)
              .addTTSListener(this)
              .build();

        // stopping playback with no registered output class does nothing
        manager.stopPlayback();

        manager = new TTSManager.Builder()
              .setTTSServiceClass("io.spokestack.spokestack.tts.TTSTestUtils$Service")
              .setOutputClass("io.spokestack.spokestack.tts.TTSTestUtils$Output")
              .setProperty("spokestack-id", "test")
              .setConfig(new SpeechConfig())
              .setAndroidContext(context)
              .addTTSListener(this)
              .build();

        LinkedBlockingQueue<String> events =
              ((TTSTestUtils.Output) manager.getOutput()).getEvents();

        manager.stopPlayback();
        assertEquals("stop", events.remove(), "stop not called on output");
    }

    @Override
    public void eventReceived(@NonNull TTSEvent event) {
        lastEvent = event;
    }
}