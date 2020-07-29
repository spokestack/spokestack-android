package io.spokestack.spokestack.asr;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SpokestackCloudRecognizerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;
    private SpeechConfig config;
    private SpokestackCloudClient client;
    private SpokestackCloudRecognizer recognizer;
    private SpokestackCloudClient.Listener listener;

    @Before
    public void before() {
        SpokestackCloudClient.Builder builder =
              spy(SpokestackCloudClient.Builder.class);
        client = mock(SpokestackCloudClient.class);
        doReturn(client).when(builder).build();

        config = createConfig();
        recognizer = new SpokestackCloudRecognizer(config, builder);

        // capture the listener
        ArgumentCaptor<SpokestackCloudClient.Listener> captor =
              ArgumentCaptor.forClass(SpokestackCloudClient.Listener.class);
        verify(builder).setListener(captor.capture());
        listener = captor.getValue();
    }

    @Test
    public void testRecognize() {
        SpeechContext context = createContext(config);

        // inactive
        recognizer.process(context, context.getBuffer().getLast());
        verify(client, never()).connect();

        // fake a socket connection to verify proper interactions
        AtomicBoolean connected = new AtomicBoolean(false);
        doAnswer(invocation -> {
            connected.set(true);
            return null;
        }).when(client).connect();
        doAnswer(invocation -> connected.get())
              .when(client).isConnected();

        // two utterances
        sendUtterance(context);
        sendUtterance(context);

        // connect called once, init called for each utterance,
        // end called for each utterance
        verify(client, times(1)).connect();
        verify(client, times(2)).init();
        verify(client, times(2)).endAudio();

        // each utterance processes the entire context buffer and one
        // additional frame
        int framesSent = (context.getBuffer().size() + 1) * 2;
        verify(client, times(framesSent))
              .sendAudio(context.getBuffer().getLast());

        // idle timeout
        for (int i = 0; i < 500; i++) {
            recognizer.process(context, context.getBuffer().getLast());
        }
        verify(client, atLeastOnce()).disconnect();

        // shutdown
        recognizer.close();
        verify(client).close();
    }

    private void sendUtterance(SpeechContext context) {
        // active/buffered frames
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());

        // subsequent frame
        recognizer.process(context, context.getBuffer().getLast());

        // complete
        context.setActive(false);
        recognizer.process(context, context.getBuffer().getLast());
    }

    @Test
    public void testResponses() {
        // process a frame to set the recognizer's context
        SpeechContext context = createContext(config);
        recognizer.process(context, context.getBuffer().getLast());

        listener.onSpeech("test one", 0.9f, false);
        assertEquals("test one", context.getTranscript());
        assertEquals(0.9f, context.getConfidence(), 1e-5);
        assertEquals(SpeechContext.Event.PARTIAL_RECOGNIZE, this.event);

        listener.onSpeech("test two", 0.9f, true);
        assertEquals("test two", context.getTranscript());
        assertEquals(0.9f, context.getConfidence(), 1e-5);
        assertEquals(SpeechContext.Event.RECOGNIZE, this.event);
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
