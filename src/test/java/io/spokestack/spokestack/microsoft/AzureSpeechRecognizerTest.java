package io.spokestack.spokestack.microsoft;

import androidx.annotation.NonNull;
import com.microsoft.cognitiveservices.speech.CancellationErrorCode;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionCanceledEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
      AudioConfig.class,
      PushAudioInputStream.class,
      SpeechRecognitionCanceledEventArgs.class,
      SpeechRecognizer.class
})
@SuppressStaticInitializationFor({
      "com.microsoft.cognitiveservices.speech.SpeechConfig",
      "com.microsoft.cognitiveservices.speech.internal.ProfanityOption",
      "com.microsoft.cognitiveservices.speech.internal.CancellationErrorCode",
      "com.microsoft.cognitiveservices.speech.internal.CancellationReason"
})
public class AzureSpeechRecognizerTest implements OnSpeechEventListener {

    private SpeechRecognizer mockRecognizer;

    private SpeechConfig speechConfig;
    private SpeechContext.Event event;
    private SpeechRecognitionEventArgs partialRecognitionEvent;
    private SpeechRecognitionEventArgs emptyTextEvent;
    private SpeechRecognitionEventArgs recognitionEvent;
    private SpeechRecognitionCanceledEventArgs canceledEvent;

    @Before
    @SuppressWarnings("rawtypes, ResultOfMethodCallIgnored")
    public void setup() {
        // MS AudioInputStream
        PowerMockito.mockStatic(AudioInputStream.class);
        when(AudioInputStream.createPushStream())
              .thenReturn(mock(PushAudioInputStream.class));

        // MS AudioConfig
        PowerMockito.mockStatic(AudioConfig.class);
        when(AudioConfig.fromStreamInput((AudioInputStream) any()))
              .thenReturn(mock(AudioConfig.class));

        // MS SpeechConfig
        PowerMockito.mockStatic(
              com.microsoft.cognitiveservices.speech.SpeechConfig.class);
        when(
              com.microsoft.cognitiveservices.speech.SpeechConfig
                    .fromSubscription(anyString(), anyString()))
              .thenReturn(PowerMockito.mock(
                    com.microsoft.cognitiveservices.speech.SpeechConfig.class));
        mockRecognizer = PowerMockito.mock(SpeechRecognizer.class);

        // we have to call `get` on the return of this method to get the
        // recognition to return a result, so this mock is a bit more complex
        Future fakeResult = mock(Future.class);
        doReturn(fakeResult).when(mockRecognizer)
              .stopContinuousRecognitionAsync();
        speechConfig = createConfig();

        // speech recognition and cancellation events
        partialRecognitionEvent =
              PowerMockito.mock(SpeechRecognitionEventArgs.class);
        SpeechRecognitionResult result = mock(SpeechRecognitionResult.class);
        doReturn("partial").when(result).getText();
        doReturn(ResultReason.RecognizingSpeech).when(result).getReason();
        when(partialRecognitionEvent.getResult()).thenReturn(result);

        recognitionEvent = PowerMockito.mock(SpeechRecognitionEventArgs.class);
        SpeechRecognitionResult finalResult =
              mock(SpeechRecognitionResult.class);
        doReturn("test").when(finalResult).getText();
        doReturn(ResultReason.RecognizedSpeech).when(finalResult).getReason();
        when(recognitionEvent.getResult()).thenReturn(finalResult);

        canceledEvent = PowerMockito.mock(SpeechRecognitionCanceledEventArgs.class);
        doReturn(CancellationReason.Error).when(canceledEvent).getReason();
        doReturn("unknown error").when(canceledEvent).getErrorDetails();
        when(canceledEvent.getErrorCode()).thenReturn(CancellationErrorCode.ServiceError);

        // empty text
        emptyTextEvent = PowerMockito.mock(SpeechRecognitionEventArgs.class);
        SpeechRecognitionResult emptyResult =
              mock(SpeechRecognitionResult.class);
        doReturn("").when(emptyResult).getText();
        doReturn(ResultReason.RecognizingSpeech).when(emptyResult).getReason();
        when(emptyTextEvent.getResult()).thenReturn(emptyResult);
    }

    @Test
    public void testConfig() {
        // invalid config
        // note that we're not testing valid configs explicitly because the
        // constructor deals with MS objects -- that code is covered by the
        // spied recognizer used in the other tests
        SpeechConfig config = createConfig();
        config.put("sample-rate", 48000);
        assertThrows(IllegalArgumentException.class,
              () -> new AzureSpeechRecognizer(config));
    }

    @Test
    public void testRecognize() throws Exception {
        AzureSpeechRecognizer azureRecognizer =
              spy(new AzureSpeechRecognizer(speechConfig));
        doReturn(mockRecognizer).when(azureRecognizer).createRecognizer(any());
        SpeechContext context = createContext(speechConfig);

        // inactive
        azureRecognizer.process(context, context.getBuffer().getLast());
        verify(azureRecognizer, never()).begin(any());

        // active/buffered frames
        context.setActive(true);
        azureRecognizer.process(context, context.getBuffer().getLast());
        verify(azureRecognizer).begin(any());
        verify(azureRecognizer, times(context.getBuffer().size()))
              .bufferFrame(context.getBuffer().getLast());

        // subsequent frame
        reset(azureRecognizer);
        azureRecognizer.process(context, context.getBuffer().getLast());
        verify(azureRecognizer).bufferFrame(context.getBuffer().getLast());

        // complete
        context.setActive(false);
        azureRecognizer.process(context, context.getBuffer().getLast());
        verify(azureRecognizer).commit();

        // shutdown
        azureRecognizer.close();
        // once for commit(), once for close()
        verify(mockRecognizer, times(2)).close();
    }

    @Test
    public void testListeners() {
        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);

        // recognition
        new AzureSpeechRecognizer.RecognitionListener(context)
              .onEvent(mockRecognizer, partialRecognitionEvent);
        assertEquals("partial", context.getTranscript());
        assertEquals(1.0, context.getConfidence());
        assertEquals(SpeechContext.Event.PARTIAL_RECOGNIZE, this.event);

        new AzureSpeechRecognizer.RecognitionListener(context)
              .onEvent(mockRecognizer, recognitionEvent);
        assertEquals("test", context.getTranscript());
        assertEquals(1.0, context.getConfidence());
        assertEquals(SpeechContext.Event.RECOGNIZE, this.event);

        this.event = null;
        new AzureSpeechRecognizer.RecognitionListener(context)
              .onEvent(mockRecognizer, emptyTextEvent);
        assertNull(this.event);

        // cancellation
        context = createContext(config);
        new AzureSpeechRecognizer.CancellationListener(context)
              .onEvent(mockRecognizer, canceledEvent);
        String code = CancellationErrorCode.ServiceError.name();
        assertEquals("unknown error (error code " + code + ")",
              context.getError().getMessage());
        assertEquals("", context.getTranscript());
        assertEquals(0, context.getConfidence());
        assertEquals(SpeechContext.Event.ERROR, this.event);
    }

    private SpeechConfig createConfig() {
        SpeechConfig config = new SpeechConfig();
        config.put("azure-api-key", "secret");
        config.put("azure-region", "mars");
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        config.put("locale", "en-US");
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
