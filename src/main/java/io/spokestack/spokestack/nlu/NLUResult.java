package io.spokestack.spokestack.nlu;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple data class that encapsulates the result of an utterance
 * classification.
 */
public final class NLUResult {
    private final String intent;
    private final float confidence;
    private final String utterance;
    private final Map<String, Object> context;
    private final Map<String, Slot> slots;
    private final Throwable error;

    private NLUResult(Builder builder) {
        this.error = builder.builderError;
        this.utterance = builder.builderUtterance;
        this.intent = builder.builderIntent;
        this.confidence = builder.builderConfidence;
        this.slots = builder.builderSlots;
        this.context = builder.builderContext;
    }

    /**
     * @return the error encountered during the NLU process.
     */
    public Throwable getError() {
        return error;
    }

    /**
     * @return The user intent.
     */
    public String getIntent() {
        return intent;
    }

    /**
     * @return The NLU's confidence in its intent prediction.
     */
    public float getConfidence() {
        return confidence;
    }

    /**
     * @return The user's original utterance.
     */
    public String getUtterance() {
        return utterance;
    }

    /**
     * @return The slot values present in the user utterance.
     */
    public Map<String, Slot> getSlots() {
        return slots;
    }

    /**
     * @return Additional context included with the classification results.
     */
    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * NLU result builder API.
     */
    public static final class Builder {

        private String builderUtterance;
        private String builderIntent;
        private float builderConfidence;
        private Map<String, Slot> builderSlots;
        private Map<String, Object> builderContext;
        private Throwable builderError;

        /**
         * Creates a new result builder.
         * @param utterance The user's original utterance.
         */
        public Builder(String utterance) {
            this.builderUtterance = utterance;
            this.builderIntent = null;
            this.builderConfidence = 0.0f;
            this.builderSlots = new HashMap<>();
            this.builderContext = new HashMap<>();
        }

        /**
         * Attaches an error encountered while performing NLU.
         * @param error The error to attach.
         * @return this
         */
        public Builder withError(Throwable error) {
            this.builderError = error;
            return this;
        }

        /**
         * Attaches the user intent classified by the NLU service.
         * @param intent The user intent.
         * @return this
         */
        public Builder withIntent(String intent) {
            this.builderIntent = intent;
            return this;
        }

        /**
         * Set the confidence for the intent classification.
         * @param confidence The classifier's confidence for its intent
         *                   prediction.
         * @return this
         */
        public Builder withConfidence(float confidence) {
            this.builderConfidence = confidence;
            return this;
        }

        /**
         * Attaches the slot values extracted from the user utterance.
         * @param slots The slots present in the user utterance.
         * @return this
         */
        public Builder withSlots(Map<String, Slot> slots) {
            this.builderSlots = slots;
            return this;
        }

        /**
         * Attaches the context returned by the NLU service.
         * @param context The context to attach.
         * @return this
         */
        public Builder withContext(Map<String, Object> context) {
            this.builderContext = context;
            return this;
        }

        /**
         * Uses the current builder state to create an NLU result.
         * @return A populated NLU result instance.
         */
        public NLUResult build() {
            return new NLUResult(this);
        }
    }
}
