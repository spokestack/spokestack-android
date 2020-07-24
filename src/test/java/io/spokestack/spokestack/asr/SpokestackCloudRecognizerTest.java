package io.spokestack.spokestack.asr;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SpokestackCloudRecognizerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    public void testRecognize() {
        SpokestackCloudClient.Builder builder =
              spy(SpokestackCloudClient.Builder.class);
        SpokestackCloudClient client = mock(SpokestackCloudClient.class);
        doReturn(client).when(builder).build();

        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);
        SpokestackCloudRecognizer recognizer =
              new SpokestackCloudRecognizer(config, builder);

        // capture the listener
        ArgumentCaptor<SpokestackCloudClient.Listener> captor =
              ArgumentCaptor.forClass(SpokestackCloudClient.Listener.class);
        verify(builder).setListener(captor.capture());
        SpokestackCloudClient.Listener listener = captor.getValue();

        // inactive
        recognizer.process(context, context.getBuffer().getLast());
        verify(client, never()).connect();

        // active/buffered frames
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client).connect();
        verify(client, times(context.getBuffer().size()))
              .sendAudio(context.getBuffer().getLast());

        // subsequent frame
        reset(client);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client).sendAudio(context.getBuffer().getLast());

        // complete
        context.setActive(false);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client).endAudio();

        // idle timeout
        for (int i = 0; i < 500; i++) {
            recognizer.process(context, context.getBuffer().getLast());
        }
        verify(client, atLeast(1)).disconnect();


        // responses
        listener.onSpeech("test", 0.9f);
        assertEquals("test", context.getTranscript());
        assertEquals(0.9f, context.getConfidence(), 1e-5);
        assertEquals(SpeechContext.Event.RECOGNIZE, this.event);

        // shutdown
        recognizer.close();
        verify(client).close();
    }

    @Test
    public void testError() {
        SpokestackCloudClient.Builder builder = spy(SpokestackCloudClient.Builder.class);
        SpokestackCloudClient client = mock(SpokestackCloudClient.class);
        doReturn(client).when(builder).build();

        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);
        SpokestackCloudRecognizer recognizer =
              new SpokestackCloudRecognizer(config, builder);

        // capture the listener
        ArgumentCaptor<SpokestackCloudClient.Listener> captor =
              ArgumentCaptor.forClass(SpokestackCloudClient.Listener.class);
        verify(builder).setListener(captor.capture());
        SpokestackCloudClient.Listener listener = captor.getValue();

        // trigger active
        context.setActive(true);
        when(client.isConnected()).thenReturn(true);
        recognizer.process(context, context.getBuffer().getLast());

        // inject fault
        listener.onError(new Exception("test error"));
        assertEquals("test error", context.getError().getMessage());
        assertEquals("", context.getTranscript());
        assertEquals(0, context.getConfidence(), 1e-5);
        assertEquals(SpeechContext.Event.ERROR, this.event);
    }

    private SpeechConfig createConfig() {
        SpeechConfig config = new SpeechConfig();
        config.put("spokestack-id", "ID");
        config.put("spokestack-secret", "secret");
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        config.put("language", "en");
        return config;
    }

    private SpeechContext createContext(SpeechConfig config) {
        SpeechContext context = new SpeechContext(config);
        context.addOnSpeechEventListener(this);

        context.attachBuffer(new LinkedList<>());
        for (int i = 0; i < 3; i++) {
            context.getBuffer().addLast(ByteBuffer.allocateDirect(320));
        }

        return context;
    }

    public void onEvent(@NonNull SpeechContext.Event event,
                        @NonNull SpeechContext context) {
        this.event = event;
    }
}
