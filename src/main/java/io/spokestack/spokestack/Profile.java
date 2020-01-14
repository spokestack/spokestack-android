package io.spokestack.spokestack;

/**
 * Convenience shortcuts to pre-built {@link PipelineProfile}s used to configure
 * the {@link SpeechPipeline}.
 */
public enum Profile {
    /**
     * Do not perform wakeword detection.
     *
     * @see io.spokestack.spokestack.profile.NoWakeword
     */
    WAKEWORD_NONE("io.spokestack.spokestack.profile.NoWakeword"),

    /**
     * Use TensorFlow Lite models for wakeword detection.
     *
     * @see io.spokestack.spokestack.profile.TFLiteWakeword
     */
    WAKEWORD_TFLITE("io.spokestack.spokestack.profile.TFLiteWakeword"),

    /**
     * Use Android's built-in {@code SpeechRecognizer} ASR.
     *
     * @see io.spokestack.spokestack.android.AndroidSpeechRecognizer
     */
    ASR_ANDROID("io.spokestack.spokestack.profile.AndroidASR"),

    /**
     * Use Google Speech for ASR.
     *
     * @see io.spokestack.spokestack.google.GoogleSpeechRecognizer
     */
    ASR_GOOGLE_CLOUD("io.spokestack.spokestack.profile.GoogleSpeechASR");

    private final String className;

    Profile(String c) {
        this.className = c;
    }

    /**
     * @return The name of the class that implements this profile.
     */
    public String getClassName() {
        return this.className;
    }
}
