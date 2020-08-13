package io.spokestack.spokestack.android;

import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import androidx.annotation.NonNull;
import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.util.EventTracer;
import io.spokestack.spokestack.util.TaskHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SpeechRecognizer.class, Bundle.class})
public class AndroidSpeechRecognizerTest {

    private final ContextWrapper emptyAppContext = mock(ContextWrapper.class);
    private final SpeechRecognizer successfulRecognizer =
          mock(SpeechRecognizer.class);
    private final SpeechRecognizer unsuccessfulRecognizer =
          mock(SpeechRecognizer.class);

    @Before
    public void before() throws Exception {
        mockStatic(SpeechRecognizer.class);

        // NOTE: this mocking strategy (establishing results in order)
        // creates a coupling between setup and test methods, but argument
        // matchers have so far been ineffective at returning the intended
        // objects
        when(SpeechRecognizer.createSpeechRecognizer(any()))
              .thenReturn(successfulRecognizer, unsuccessfulRecognizer,
                    successfulRecognizer);
        whenNew(Intent.class).withAnyArguments().thenReturn(mock(Intent.class));
        configureRecognizer(successfulRecognizer, new MockRecognizer(true));
        configureRecognizer(unsuccessfulRecognizer, new MockRecognizer(false));
        when(emptyAppContext.getApplicationContext()).thenReturn(null);
    }

    private void configureRecognizer(SpeechRecognizer target,
                                     MockRecognizer mockRecognizer) {
        // we can't subclass SpeechRecognizer because the stubbed constructor
        // throws, so we have to proxy its relevant methods to our mock instead
        doAnswer(invocation -> {
                  mockRecognizer.startListening(null);
                  return null;
              }
        ).when(target).startListening(any());
        doAnswer(invocation -> {
                  RecognitionListener listener = invocation.getArgument(0);
                  mockRecognizer.setRecognitionListener(listener);
                  return null;
              }
        ).when(target).setRecognitionListener(any());
    }

    @Test
    public void testClose() {
        SpeechConfig config = new SpeechConfig();
        config.put("trace-level", EventTracer.Level.DEBUG.value());
        config.put("min-active", 500);
        AndroidSpeechRecognizer speechRecognizer =
              spy(new AndroidSpeechRecognizer(config, new TaskHandler(false)));
        doReturn(null).when(speechRecognizer).createRecognitionIntent();

        // closing before establishing a recognizer is safe
        assertDoesNotThrow(speechRecognizer::close);

        SpeechContext context = new SpeechContext(config);
        context.setActive(true);
        ByteBuffer frame = ByteBuffer.allocateDirect(32);

        // ASR active
        speechRecognizer.process(context, frame);

        // closing still safe
        assertDoesNotThrow(speechRecognizer::close);
    }

    @Test
    public void testProcess() {
        SpeechConfig config = new SpeechConfig();
        config.put("trace-level", EventTracer.Level.DEBUG.value());
        config.put("min-active", 500);
        AndroidSpeechRecognizer speechRecognizer =
              spy(new AndroidSpeechRecognizer(config, new TaskHandler(false)));
        doReturn(null).when(speechRecognizer).createRecognitionIntent();
        SpeechContext context = new SpeechContext(config);
        EventListener listener = new EventListener();
        context.addOnSpeechEventListener(listener);
        ByteBuffer frame = ByteBuffer.allocateDirect(32);

        // ASR inactive
        speechRecognizer.process(context, frame);
        assertNull(listener.transcript);
        assertFalse(listener.receivedPartial);
        assertNull(listener.error);

        // ASR active
        AndroidSpeechRecognizer.SpokestackListener asrListener =
              speechRecognizer.getListener();

        // partial result
        listener.clear();
        Bundle results = MockRecognizer.speechResults("partial");
        asrListener.onPartialResults(results);
        assertEquals(MockRecognizer.TRANSCRIPT, listener.transcript);
        assertTrue(listener.receivedPartial);
        assertNull(listener.error);

        // full result
        listener.clear();
        context.setActive(true);
        speechRecognizer.process(context, frame);
        assertEquals(MockRecognizer.TRANSCRIPT, listener.transcript);
        assertFalse(listener.receivedPartial);
        assertNull(listener.error);

        // make sure all the events fired, but only once because they
        // shouldn't fire when ASR is inactive
        assertEquals(6, listener.traces.size());

        // ASR received an error
        listener.clear();
        context.setActive(true);
        speechRecognizer =
              spy(new AndroidSpeechRecognizer(config, new TaskHandler(false)));
        doReturn(null).when(speechRecognizer).createRecognitionIntent();
        speechRecognizer.process(context, frame);
        assertNull(listener.transcript);
        assertEquals(SpeechRecognizerError.class, listener.error.getClass());
        String expectedError =
              SpeechRecognizerError.Description.SERVER_ERROR.toString();
        assertTrue(listener.error.getMessage().contains(expectedError));

        // closing the component has no effect (doubly so because its internal
        // system speech recognizer is mocked here)
        speechRecognizer.close();
    }

    @Test
    public void testContextManagement() {
        SpeechConfig config = new SpeechConfig();
        config.put("trace-level", EventTracer.Level.DEBUG.value());
        config.put("min-active", 500);
        AndroidSpeechRecognizer speechRecognizer =
              spy(new AndroidSpeechRecognizer(config, new TaskHandler(false)));
        doReturn(null).when(speechRecognizer).createRecognitionIntent();
        AndroidSpeechRecognizer.SpokestackListener asrListener =
              speechRecognizer.getListener();
        SpeechContext context = new SpeechContext(config);
        EventListener eventListener = new EventListener();
        context.addOnSpeechEventListener(eventListener);
        ByteBuffer frame = ByteBuffer.allocateDirect(32);

        // listener not created before the first frame is processed
        assertNull(asrListener);

        speechRecognizer.process(context, frame);
        asrListener = speechRecognizer.getListener();
        assertNotNull(asrListener);
        assertFalse(context.isActive());
        assertFalse(context.isSpeech());

        asrListener.onBeginningOfSpeech();
        assertTrue(context.isSpeech());

        asrListener.onEndOfSpeech();
        assertFalse(context.isSpeech());

        // restart speech, then throw some errors
        asrListener.onBeginningOfSpeech();
        assertTrue(context.isSpeech());

        asrListener.onError(
              SpeechRecognizerError.Description.SPEECH_TIMEOUT.ordinal());
        assertFalse(context.isActive());
        assertFalse(context.isSpeech());
        assertNull(eventListener.error);
        int numTraces = eventListener.traces.size();
        assertEquals(eventListener.traces.get(numTraces - 1),
              EventListener.TIMEOUT);

        // recognition match -> timeout (presumed Google bug)
        eventListener.clear();
        asrListener.onError(
              SpeechRecognizerError.Description.NO_RECOGNITION_MATCH.ordinal());
        assertNull(eventListener.error);
        numTraces = eventListener.traces.size();
        assertEquals(eventListener.traces.get(numTraces - 1),
              EventListener.TIMEOUT);

        context.setActive(true);
        context.setSpeech(true);

        asrListener.onError(
              SpeechRecognizerError.Description.SERVER_ERROR.ordinal());
        assertFalse(context.isActive());
        assertFalse(context.isSpeech());
        assertEquals(SpeechRecognizerError.class, eventListener.error.getClass());
        String expectedError =
              SpeechRecognizerError.Description.SERVER_ERROR.toString();
        assertTrue(eventListener.error.getMessage().contains(expectedError));
    }


    private static class EventListener implements OnSpeechEventListener {
        static final String TIMEOUT = "timeout";

        String transcript;
        List<String> traces = new ArrayList<>();
        double confidence;
        Throwable error;
        boolean receivedPartial;

        EventListener() {
        }

        public void clear() {
            this.transcript = null;
            this.confidence = 0.0;
            this.error = null;
            this.receivedPartial = false;
        }

        @Override
        public void onEvent(@NonNull SpeechContext.Event event,
                            @NonNull SpeechContext context) {
            switch (event) {
                case PARTIAL_RECOGNIZE:
                    this.receivedPartial = true;
                case RECOGNIZE:
                    this.transcript = context.getTranscript();
                    this.confidence = context.getConfidence();
                    break;
                case ERROR:
                    this.error = context.getError();
                    break;
                case TIMEOUT:
                    this.traces.add(TIMEOUT);
                    break;
                case TRACE:
                    this.traces.add(context.getMessage());
                    break;
                default:
                    break;
            }

        }
    }
}