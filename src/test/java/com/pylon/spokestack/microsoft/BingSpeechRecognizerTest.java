package com.pylon.spokestack.microsoft;

import java.util.*;
import java.nio.ByteBuffer;

import org.junit.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

import com.pylon.spokestack.OnSpeechEventListener;
import com.pylon.spokestack.SpeechConfig;
import com.pylon.spokestack.SpeechContext;

public class BingSpeechRecognizerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    public void testRecognize() throws Exception {
        BingSpeechClient.Builder builder = spy(BingSpeechClient.Builder.class);
        BingSpeechClient client = mock(BingSpeechClient.class);
        doReturn(client).when(builder).build();

        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);
        BingSpeechRecognizer recognizer =
            new BingSpeechRecognizer(config, builder);

        // capture the listener
        ArgumentCaptor<BingSpeechClient.Listener> captor =
            ArgumentCaptor.forClass(BingSpeechClient.Listener.class);
        verify(builder)
            .setListener(captor.capture());
        BingSpeechClient.Listener listener = captor.getValue();

        // inactive
        recognizer.process(context, context.getBuffer().getLast());
        verify(client, never())
            .beginAudio();

        // active/buffered frames
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client)
            .beginAudio();
        verify(client, times(context.getBuffer().size()))
            .sendAudio(context.getBuffer().getLast());

        // subsequent frame
        reset(client);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client)
            .sendAudio(context.getBuffer().getLast());

        // complete
        context.setActive(false);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client)
            .endAudio();

        // idle timeout
        for (int i = 0; i < 500; i++)
            recognizer.process(context, context.getBuffer().getLast());
        verify(client, atLeast(1))
            .disconnect();


        // responses
        listener.onSpeech("test");
        assertEquals("test", context.getTranscript());
        assertEquals(1.0, context.getConfidence());
        assertEquals(SpeechContext.Event.RECOGNIZE, this.event);

        // shutdown
        recognizer.close();
        verify(client).close();
    }

    @Test
    public void testError() throws Exception {
        BingSpeechClient.Builder builder = spy(BingSpeechClient.Builder.class);
        BingSpeechClient client = mock(BingSpeechClient.class);
        doReturn(client).when(builder).build();

        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);
        BingSpeechRecognizer recognizer =
            new BingSpeechRecognizer(config, builder);

        // capture the listener
        ArgumentCaptor<BingSpeechClient.Listener> captor =
            ArgumentCaptor.forClass(BingSpeechClient.Listener.class);
        verify(builder)
            .setListener(captor.capture());
        BingSpeechClient.Listener listener = captor.getValue();

        // trigger active
        context.setActive(true);
        when(client.isConnected())
            .thenReturn(true);
        recognizer.process(context, context.getBuffer().getLast());

        // inject fault
        listener.onError(new Exception("test error"));
        assertEquals("test error", context.getError().getMessage());
        assertEquals("", context.getTranscript());
        assertEquals(0, context.getConfidence());
        assertEquals(SpeechContext.Event.ERROR, this.event);
    }

    private SpeechConfig createConfig() {
        SpeechConfig config = new SpeechConfig();
        config.put("bing-speech-api-key", "secret");
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        config.put("locale", "en-US");
        return config;
    }

    private SpeechContext createContext(SpeechConfig config) {
        SpeechContext context = new SpeechContext(config);
        context.addOnSpeechEventListener(this);

        context.attachBuffer(new LinkedList<ByteBuffer>());
        for (int i = 0; i < 3; i++)
            context.getBuffer().addLast(ByteBuffer.allocateDirect(320));

        return context;
    }

    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
    }
}
