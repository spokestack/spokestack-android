package io.spokestack.spokestack.nlu;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple data class that encapsulates the result of an utterance
 * classification.
 */
public final class NLUResult {
    private final String intent;
    private final String utterance;
    private final Map<String, Object> context;
    private final Map<String, Slot> slots;
    private final Throwable error;

    private NLUResult(String builderUtterance, String builderIntent,
                      Map<String, Slot> builderSlots,
                      Map<String, Object> builderContext) {
        this.intent = builderIntent;
        this.utterance = builderUtterance;
        this.slots = builderSlots;
        this.context = builderContext;
        this.error = null;
    }

    private NLUResult(String builderUtterance, Throwable builderError) {
        this.utterance = builderUtterance;
        this.error = builderError;
        this.intent = null;
        this.slots = null;
        this.context = null;
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
        private String builderIntent = "";
        private Map<String, Slot> builderSlots = new HashMap<>();
        private Map<String, Object> builderContext = new HashMap<>();
        private Throwable builderError;

        /**
         * Creates a new result builder.
         * @param utterance The user's original utterance.
         */
        public Builder(String utterance) {
            this.builderUtterance = utterance;
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
            if (builderError != null) {
                return new NLUResult(builderUtterance, builderError);
            }
            return new NLUResult(builderUtterance, builderIntent,
                  builderSlots, builderContext);
        }
    }
}
