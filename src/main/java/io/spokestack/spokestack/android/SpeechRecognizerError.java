package io.spokestack.spokestack.android;

/**
 * A simple exception class that wraps error codes from {@code
 * android.speech.SpeechRecognizer}.
 */
public class SpeechRecognizerError extends Exception {

    /**
     * The description of the Android system error code.
     */
    public final Description description;

    /**
     * Create a new SpeechRecognizerError from an error code provided by the
     * Android system.
     *
     * @param errorCode The Android system error code.
     */
    public SpeechRecognizerError(int errorCode) {
        super("SpeechRecognizer error code " + errorCode + ": "
              + SpeechRecognizerError.errorDescription(errorCode));
        this.description = SpeechRecognizerError.errorDescription(errorCode);
    }

    private static Description errorDescription(int errorCode) {
        if (errorCode < Description.VALUES.length) {
            return Description.VALUES[errorCode];
        } else {
            return Description.UNKNOWN_ERROR;
        }
    }

    /**
     * An enumeration of the SpeechRecognizer error descriptions aligned with
     * their integer constant values.
     */
    @SuppressWarnings("checkstyle:javadocvariable")
    public enum Description {
        UNKNOWN_ERROR,
        NETWORK_TIMEOUT,
        NETWORK_ERROR,
        AUDIO_RECORDING_ERROR,
        SERVER_ERROR,
        CLIENT_ERROR,
        SPEECH_TIMEOUT,
        NO_RECOGNITION_MATCH,
        RECOGNIZER_BUSY,
        INSUFFICIENT_PERMISSIONS;

        /**
         * A cache of the error descriptions to reduce overhead accessing them.
         */
        public static final Description[] VALUES = values();
    }
}
