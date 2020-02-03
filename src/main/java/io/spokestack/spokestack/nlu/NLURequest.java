package io.spokestack.spokestack.nlu;

/**
 * A data class that represents information sent to an NLU service for
 * classification.
 */
public final class NLURequest {

    private final String utterance;

    /**
     * Create a new NLU request for classification.
     * @param userUtterance The user utterance to be classified.
     */
    private NLURequest(String userUtterance) {
        this.utterance = userUtterance;
    }

    /**
     * @return The user utterance for this request.
     */
    public String getUtterance() {
        return utterance;
    }
}
