package com.pylon.spokestack.google;

import java.util.*;
import java.nio.ByteBuffer;

import org.junit.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.speech.v1.stub.SpeechStub;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;

import com.pylon.spokestack.OnSpeechEventListener;
import com.pylon.spokestack.SpeechConfig;
import com.pylon.spokestack.SpeechContext;

public class GoogleSpeechRecognizerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    @SuppressWarnings("unchecked")
    public void testRecognize() throws Exception {
        SpeechContext context = createContext();
        MockSpeechClient client = spy(MockSpeechClient.class);
        GoogleSpeechRecognizer recognizer =
            new GoogleSpeechRecognizer(createConfig(), client);

        // inactive
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests(), never())
            .onNext(any(StreamingRecognizeRequest.class));

        // active/buffered frames
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests(), times(context.getBuffer().size() + 1))
            .onNext(any(StreamingRecognizeRequest.class));

        // subsequent frame
        reset(client.getRequests());
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests())
            .onNext(any(StreamingRecognizeRequest.class));

        // complete
        context.setActive(false);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests())
            .onCompleted();

        // responses
        client.getResponses().onNext(StreamingRecognizeResponse.newBuilder()
            .addResults(StreamingRecognitionResult.newBuilder()
                .addAlternatives(SpeechRecognitionAlternative.newBuilder()
                    .setTranscript("test")
                    .setConfidence((float)0.75)
                    .build())
                .build())
            .build()
        );
        client.getResponses().onCompleted();
        assertEquals("test", context.getTranscript());
        assertEquals(0.75, context.getConfidence());
        assertEquals(SpeechContext.Event.RECOGNIZE, this.event);

        // shutdown
        recognizer.close();
        verify(client.getStub()).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testError() throws Exception {
        SpeechContext context = createContext();
        MockSpeechClient client = spy(MockSpeechClient.class);
        GoogleSpeechRecognizer recognizer =
            new GoogleSpeechRecognizer(createConfig(), client);

        // trigger recognition
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());
        context.setActive(false);
        recognizer.process(context, context.getBuffer().getLast());

        // inject fault
        client.getResponses().onError(new Exception("test error"));
        assertEquals("test error", context.getError().getMessage());
        assertEquals("", context.getTranscript());
        assertEquals(0, context.getConfidence());
        assertEquals(SpeechContext.Event.ERROR, this.event);
    }

    private SpeechConfig createConfig() {
        SpeechConfig config = new SpeechConfig();
        config.put("google-credentials", "{}");
        config.put("sample-rate", 16000);
        config.put("locale", "en-US");
        return config;
    }

    private SpeechContext createContext() {
        SpeechContext context = new SpeechContext();
        context.addOnSpeechEventListener(this);

        context.attachBuffer(new LinkedList<ByteBuffer>());
        for (int i = 0; i < 3; i++)
            context.getBuffer().addLast(ByteBuffer.allocateDirect(320));

        return context;
    }

    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
    }

    @SuppressWarnings("unchecked")
    private static class MockSpeechClient extends SpeechClient {
        private final BidiStreamingCallable callable;
        private final ApiStreamObserver requests;

        public MockSpeechClient() {
            super(mock(SpeechStub.class));
            this.callable = mock(BidiStreamingCallable.class);
            this.requests = mock(ApiStreamObserver.class);
            when(getStub().streamingRecognizeCallable())
                .thenReturn(this.callable);
            when(this.callable.bidiStreamingCall(any(ApiStreamObserver.class)))
                .thenReturn(this.requests);
        }

        public ApiStreamObserver getRequests() {
            return this.requests;
        }

        public ApiStreamObserver getResponses() {
            ArgumentCaptor<ApiStreamObserver> captor =
                ArgumentCaptor.forClass(ApiStreamObserver.class);
            verify(this.callable)
                .bidiStreamingCall(captor.capture());
            return captor.getValue();
        }
    }
}
