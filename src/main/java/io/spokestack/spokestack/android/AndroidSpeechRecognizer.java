package io.spokestack.spokestack.android;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.util.TaskHandler;

import java.nio.ByteBuffer;
import java.util.ArrayList;


/**
 * Speech recognition using built-in Android APIs.
 *
 * <p>
 * This component uses the built-in Android {@code SpeechRecognizer} to process
 * user speech.
 * </p>
 *
 * <p>
 * As part of normal operation, {@code SpeechRecognizer} plays system sounds
 * both when it starts and stops actively listening to the user, just like the
 * built-in Google Assistant. This behavior is not optional; it can be
 * suppressed by having the {@code AudioManager} mute the music stream, but it
 * muting and restoring the volume of that stream at exactly the right times is
 * error-prone, so such behavior has been omitted from this component.
 * </p>
 *
 * <p>
 * Note that this component requires an Android {@code Context} to be attached
 * to the pipeline that has created it. If the pipeline is meant to persist
 * across different {@code Activity}s, the {@code Context} used must either be
 * the <em>application</em> context, or it must be re-set on the pipeline's
 * {@code SpeechContext} object when the Activity context changes.
 * </p>
 *
 * <p>
 * Implementation of {@code SpeechRecognizer} is left up to devices, and even
 * though the API exists, an actual recognizer may not be present on all
 * devices. If using this component, it's a good idea to call {@code
 * SpeechRecognizer.isRecognitionAvailable()} before adding it to the pipeline
 * to determine whether it will be viable on the current device.
 * </p>
 *
 * <p>
 * In addition, testing has shown that some older devices may return {@code
 * true} for the preceding call but have outdated implementations that
 * consistently throw errors. For this reason, it's a good idea to have an
 * {@link io.spokestack.spokestack.OnSpeechEventListener} set up to detect
 * {@link SpeechRecognizerError}s and have an appropriate fallback strategy in
 * place.
 * </p>
 *
 * <p>
 * This pipeline component supports the following configuration property, though
 * it should be left at its default setting in most circumstances:
 * </p>
 * <ul>
 *   <li>
 *      <b>wake-active-min</b> (integer): the minimum length of time, in
 *      milliseconds, that the recognizer should wait for speech before timing
 *      out.
 *   </li>
 * </ul>
 *
 * <p>
 * The {@code wake-active-min} parameter merely sets a hint for the
 * {@code Intent} used to start recognition, and Google
 * <a href="https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS">
 * does not guarantee</a> that this hint will be honored on all devices.
 * </p>
 */
public class AndroidSpeechRecognizer implements SpeechProcessor {
    private final int minActive;

    private boolean streaming;
    private SpeechRecognizer speechRecognizer;
    private SpokestackListener listener;
    private TaskHandler taskHandler;

    /**
     * Initializes a new recognizer.
     *
     * @param speechConfig Spokestack pipeline configuration
     */
    public AndroidSpeechRecognizer(SpeechConfig speechConfig) {
        this.streaming = false;
        this.minActive = speechConfig.getInteger("wake-active-min", 0);
        this.taskHandler = new TaskHandler(true);
    }

    /**
     * Create an instance of the recognizer with an injected {@link
     * TaskHandler}. Used for testing.
     *
     * @param speechConfig Spokestack pipeline configuration
     * @param handler      The task handler used to interact with the speech
     *                     recognizer.
     */
    AndroidSpeechRecognizer(SpeechConfig speechConfig,
                            TaskHandler handler) {
        this(speechConfig);
        this.taskHandler = handler;
    }

    /**
     * @return The internal {@code RecognitionListener}. Used for testing.
     */
    SpokestackListener getListener() {
        return this.listener;
    }

    @Override
    public void process(SpeechContext context, ByteBuffer frame) {
        if (this.speechRecognizer == null) {
            createRecognizer(context);
        }

        if (context.isActive()) {
            if (!this.streaming) {
                context.setManaged(true);
                begin();
                this.streaming = true;
            }
        } else {
            this.streaming = false;
        }
    }

    private void createRecognizer(SpeechContext context) {
        this.taskHandler.run(() -> {
            Context androidContext = context.getAndroidContext();
            this.speechRecognizer =
                  SpeechRecognizer.createSpeechRecognizer(androidContext);
            this.listener = new SpokestackListener(context);
            this.speechRecognizer.setRecognitionListener(this.listener);
        });
    }

    private void begin() {
        this.taskHandler.run(() -> {
            Intent recognitionIntent = createRecognitionIntent();
            this.speechRecognizer.startListening(recognitionIntent);
        });
    }

    Intent createRecognitionIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
              RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (this.minActive > 0) {
            intent.putExtra(
                  RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                  this.minActive);
        }
        return intent;
    }

    @Override
    public void reset() {
       close();
    }

    @Override
    public void close() {
        this.taskHandler.run(() -> {
            if (this.speechRecognizer != null) {
                this.speechRecognizer.destroy();
                this.speechRecognizer = null;
            }
        });
    }

    /**
     * An internal listener used to dispatch events from the Android speech
     * recognizer to the Spokestack {@link SpeechContext}.
     */
    static class SpokestackListener implements RecognitionListener {
        private final SpeechContext context;

        SpokestackListener(SpeechContext speechContext) {
            this.context = speechContext;
        }

        @Override
        public void onError(int error) {
            SpeechRecognizerError speechErr = new SpeechRecognizerError(error);
            this.context.traceDebug("AndroidSpeechRecognizer error " + error);
            if (isTimeout(speechErr.description)) {
                this.context.dispatch(SpeechContext.Event.TIMEOUT);
            } else {
                this.context.setError(speechErr);
                this.context.dispatch(SpeechContext.Event.ERROR);
            }
            relinquishContext();
        }

        private boolean isTimeout(
              SpeechRecognizerError.Description description) {
            // the NO_RECOGNITION_MATCH condition appears to be a bug on
            // Google's part that cropped up since this class was written,
            // but we'll leave the workaround in place unless/until they fix it
            return description
                  == SpeechRecognizerError.Description.SPEECH_TIMEOUT
                  || description
                  == SpeechRecognizerError.Description.NO_RECOGNITION_MATCH;
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            dispatchRecognition(partialResults, false);
        }

        @Override
        public void onResults(Bundle results) {
            dispatchRecognition(results, true);
            relinquishContext();
        }

        private void dispatchRecognition(Bundle results, boolean isFinal) {
            SpeechContext.Event event = (isFinal)
                  ? SpeechContext.Event.RECOGNIZE
                  : SpeechContext.Event.PARTIAL_RECOGNIZE;
            String transcript = extractTranscript(results);
            if (!transcript.equals("")) {
                float confidence = extractConfidence(results);
                this.context.setTranscript(transcript);
                this.context.setConfidence(confidence);
                this.context.dispatch(event);
            } else if (isFinal) {
                this.context.dispatch(SpeechContext.Event.TIMEOUT);
            }
        }

        private String extractTranscript(Bundle results) {
            ArrayList<String> nBest = results.getStringArrayList(
                  SpeechRecognizer.RESULTS_RECOGNITION);
            return nBest.get(0);
        }

        private float extractConfidence(Bundle results) {
            float[] confidences = results.getFloatArray(
                  SpeechRecognizer.CONFIDENCE_SCORES);
            if (confidences == null || confidences.length == 0) {
                return 0.0f;
            }
            return confidences[0];
        }

        private void relinquishContext() {
            this.context.setSpeech(false);
            this.context.setActive(false);
            this.context.setManaged(false);
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            this.context.traceDebug(
                  "AndroidSpeechRecognizer ready for speech");
        }

        @Override
        public void onBeginningOfSpeech() {
            this.context.setSpeech(true);
            this.context.traceDebug("AndroidSpeechRecognizer begin speech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            this.context.traceDebug("AndroidSpeechRecognizer RMS %f", rmsdB);
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            this.context.traceDebug("AndroidSpeechRecognizer buffer received");
        }

        @Override
        public void onEndOfSpeech() {
            this.context.traceDebug("AndroidSpeechRecognizer end speech");
            this.context.setSpeech(false);
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            this.context.traceDebug(
                  "AndroidSpeechRecognizer event: %d", eventType);
        }
    }
}
