package io.spokestack.spokestack.android;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A fake {@link SpeechRecognizer} used for testing.
 */
public class MockRecognizer {
    public static final String TRANSCRIPT = "test";
    private boolean isSuccessful;
    private RecognitionListener recognitionListener;

    MockRecognizer(boolean successful) {
        this.isSuccessful = successful;
    }

    /**
     * Set a recognition listener to receive fake results/errors.
     *
     * @param listener The listener that should receive recognition results.
     */
    public void setRecognitionListener(RecognitionListener listener) {
        this.recognitionListener = listener;
    }

    /**
     * Immediately return either results or an error from a pretend speech
     * recognition session. Note that since the listener expects a proper
     * {@code Bundle} (which is a final class stubbed by Android for testing),
     * this method will not work unless the test class it's used in includes
     * PowerMock's {@link org.junit.runner.RunWith} and
     * {@link org.powermock.core.classloader.annotations.PrepareForTest}
     * annotations.
     *
     * @param recognitionIntent the intent used to start recognition. Unused by
     *                          this mock.
     */
    @SuppressWarnings("unused")
    public void startListening(Intent recognitionIntent) {
        if (this.isSuccessful) {
            Bundle results = mock(Bundle.class);
            ArrayList<String> nBest =
                  new ArrayList<>( Arrays.asList(TRANSCRIPT, "testy"));
            when(results
                  .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                  .thenReturn(nBest);
            float[] confidences = new float[]{.85f, .15f};
            when(results
                  .getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES))
                  .thenReturn(confidences);
            recognitionListener.onResults(results);
        } else {
            recognitionListener.onError(4);
        }

        // this is a terrible thing to do to just improve test coverage for
        // unimplemented methods, but it will trigger a revisiting of this
        // test if the methods are implemented in the future
        recognitionListener.onReadyForSpeech(null);
        recognitionListener.onBeginningOfSpeech();
        recognitionListener.onRmsChanged(0);
        recognitionListener.onBufferReceived(new byte[]{});
        recognitionListener.onEndOfSpeech();
        recognitionListener.onPartialResults(null);
        recognitionListener.onEvent(0, null);
    }
}
