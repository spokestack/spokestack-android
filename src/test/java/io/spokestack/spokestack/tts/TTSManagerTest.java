package io.spokestack.spokestack.tts;


import android.content.Context;
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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class TTSManagerTest implements TTSListener {

    @Mock
    private Context context = mock(Context.class);

    private TTSEvent lastEvent;
    private static LinkedBlockingQueue<String> lifecycleEvents;

    @Before
    public void before() {
        lastEvent = null;
        lifecycleEvents = new LinkedBlockingQueue<>();
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
        TTSManager manager = new TTSManager.Builder(context)
              .setTTSServiceClass("io.spokestack.spokestack.tts.SpokestackTTSService")
              .setOutputClass("io.spokestack.spokestack.tts.TTSManagerTest$Output")
              .setProperty("spokestack-key", "test")
              .addTTSListener(this)
              .build();

        LifecycleRegistry lifecycleRegistry =
              new LifecycleRegistry(mock(LifecycleOwner.class));
        manager.registerLifecycle(lifecycleRegistry);

        manager.prepare();
        assertNotNull(manager.getTtsService());
        assertNotNull(manager.getOutput());

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        String event = lifecycleEvents.poll(1, TimeUnit.SECONDS);
        assertEquals("onResume", event, "onResume not called");

        manager.close();
        assertNull(manager.getTtsService());
        assertNull(manager.getOutput());

        // re-preparation is allowed
        manager.prepare();
        assertNotNull(manager.getTtsService());
        assertNotNull(manager.getOutput());

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        event = lifecycleEvents.poll(1, TimeUnit.SECONDS);
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

    public static class Output implements SpeechOutput, DefaultLifecycleObserver {

        public Output(SpeechConfig config) { }

        @Override
        public void onResume(@NotNull LifecycleOwner owner) {
            lifecycleEvents.add("onResume");
        }

        @Override
        public void audioReceived(AudioResponse response) { }

        @Override
        public void setAppContext(Context appContext) { }

        @Override
        public void close() {
            throw new RuntimeException("can't close won't close");
        }
    }
}