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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class TTSManagerTest implements TTSListener {

    @Mock
    private final Context context = mock(Context.class);

    private List<TTSEvent> events = new ArrayList<>();

    @Before
    public void before() {
        events.clear();
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
              .setConfig(new SpeechConfig())
              .setAndroidContext(context)
              .addTTSListener(this)
              .build();

        LinkedBlockingQueue<String> outputEvents =
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
        String event = outputEvents.poll(1, TimeUnit.SECONDS);
        assertEquals("audioReceived", event, "audioReceived not called");
        TTSEvent lastEvent = this.events.get(0);
        assertEquals(TTSEvent.Type.AUDIO_AVAILABLE, lastEvent.type);
        assertEquals(Uri.EMPTY, lastEvent.getTtsResponse().getAudioUri());

        manager.close();
        assertNull(manager.getTtsService());
        assertNull(manager.getOutput());

        this.events.clear();

        // re-preparation is allowed
        manager.prepare();
        assertNotNull(manager.getTtsService());
        assertNotNull(manager.getOutput());

        String errorMsg = "can't close won't close";
        manager.close();
        lastEvent = this.events.get(0);
        assertEquals(TTSEvent.Type.ERROR, lastEvent.type);
        assertEquals(errorMsg, lastEvent.getError().getMessage());
    }

    @Test
    public void testStop() throws Exception {
        TTSManager manager = new TTSManager.Builder()
              .setTTSServiceClass("io.spokestack.spokestack.tts.TTSTestUtils$Service")
              .setConfig(new SpeechConfig())
              .setAndroidContext(context)
              .addTTSListener(this)
              .build();

        // stopping playback with no registered output class does nothing
        manager.stopPlayback();

        manager = new TTSManager.Builder()
              .setTTSServiceClass("io.spokestack.spokestack.tts.TTSTestUtils$Service")
              .setOutputClass("io.spokestack.spokestack.tts.TTSTestUtils$Output")
              .setConfig(new SpeechConfig())
              .setAndroidContext(context)
              .addTTSListener(this)
              .build();

        LinkedBlockingQueue<String> events =
              ((TTSTestUtils.Output) manager.getOutput()).getEvents();

        manager.stopPlayback();
        assertEquals("stop", events.remove(), "stop not called on output");
    }

    @Test
    public void testQueue() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);
        TTSManager manager = new TTSManager.Builder()
              .setTTSServiceClass("io.spokestack.spokestack.tts.TTSTestUtils$Service")
              .setConfig(new SpeechConfig())
              .setProperty("service-latch", latch)
              .setAndroidContext(context)
              .addTTSListener(this)
              .build();

        SynthesisRequest request = new SynthesisRequest.Builder("test")
              .build();

        TTSTestUtils.Service ttsService =
              (TTSTestUtils.Service) manager.getTtsService();

        new Thread(() -> manager.synthesize(request)).start();
        new Thread(() -> manager.synthesize(request)).start();
        latch.await(1, TimeUnit.SECONDS);

        String[] expected =
              new String[] {"synthesize", "deliver", "synthesize", "deliver"};
        assertEquals(4, ttsService.calls.size());
        for (int i = 0; i < ttsService.calls.size(); i++) {
            assertEquals(expected[i], ttsService.calls.get(i),
                  "failed at item " + i);
        }
    }

    @Override
    public void eventReceived(@NonNull TTSEvent event) {
        this.events.add(event);
    }
}
