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
 */
public final class AndroidSpeechRecognizer implements SpeechProcessor {
    private boolean streaming;
    private SpeechRecognizer speechRecognizer;
    private TaskHandler taskHandler;

    /**
     * Initializes a new recognizer.
     *
     * @param speechConfig Spokestack pipeline configuration
     */
    @SuppressWarnings("unused")
    public AndroidSpeechRecognizer(SpeechConfig speechConfig) {
        this.streaming = false;
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

    @Override
    public void process(SpeechContext context, ByteBuffer frame) {
        if (this.speechRecognizer == null) {
            createRecognizer(context);
        }

        if (context.isActive()) {
            if (!this.streaming) {
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
            this.speechRecognizer.setRecognitionListener(
                  new SpokestackListener(context));
        });
    }

    private void begin() {
        this.taskHandler.run(() -> {
            Intent recognitionIntent = createRecognitionIntent();
            this.speechRecognizer.startListening(recognitionIntent);
        });
    }

    private Intent createRecognitionIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
              RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // added in API level 23
        intent.putExtra("android.speech.extra.PREFER_OFFLINE", true);
        return intent;
    }

    @Override
    public void close() {
        this.taskHandler.run(() -> this.speechRecognizer.destroy());
    }

    /**
     * An internal listener used to dispatch events from the Android speech
     * recognizer to the Spokestack {@link SpeechContext}.
     */
    private static class SpokestackListener implements RecognitionListener {
        private final SpeechContext context;

        SpokestackListener(SpeechContext speechContext) {
            this.context = speechContext;
        }

        @Override
        public void onError(int error) {
            this.context.setError(new SpeechRecognizerError(error));
            this.context.dispatch(SpeechContext.Event.ERROR);
        }

        @Override
        public void onResults(Bundle results) {
            String transcript = extractTranscript(results);
            float confidence = extractConfidence(results);
            this.context.setTranscript(transcript);
            this.context.setConfidence(confidence);
            this.context.dispatch(SpeechContext.Event.RECOGNIZE);
        }

        private String extractTranscript(Bundle results) {
            ArrayList<String> nBest = results.getStringArrayList(
                  SpeechRecognizer.RESULTS_RECOGNITION);
            return nBest.get(0);
        }

        private float extractConfidence(Bundle results) {
            float[] confidences = results.getFloatArray(
                  SpeechRecognizer.CONFIDENCE_SCORES);
            return confidences.length > 0 ? confidences[0] : 0.0f;
        }

        // other methods required by RecognitionListener but useless for our
        // current purposes

        @Override
        public void onReadyForSpeech(Bundle params) { }

        @Override
        public void onBeginningOfSpeech() { }

        @Override
        public void onRmsChanged(float rmsdB) { }

        @Override
        public void onBufferReceived(byte[] buffer) { }

        @Override
        public void onEndOfSpeech() { }

        @Override
        public void onPartialResults(Bundle partialResults) { }

        @Override
        public void onEvent(int eventType, Bundle params) { }
    }
}
