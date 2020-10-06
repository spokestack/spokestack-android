package io.spokestack.spokestack.asr;

import com.google.gson.annotations.SerializedName;

/**
 * A simple data class for deserializing responses from Spokestack's cloud-based
 * ASR service.
 */
public class SpokestackASRResponse {

    /**
     * does the hypothesis represent the full transcript for the transmitted
     * audio?
     */
    @SerializedName("final")
    public final boolean isFinal;

    /**
     * server status: "ok" or "error".
     */
    public final String status;

    /**
     * error message from server (if status is "error").
     */
    public final String error;

    /**
     * n-best list of transcripts and confidences (the value of n is determined
     * by the client).
     */
    public final Hypothesis[] hypotheses;

    /**
     * Create a new ASR response. Used for testing.
     * @param serverStatus status response from server
     * @param err error message
     * @param finalResponse is this a full transcript?
     * @param nBest list of asr hypotheses
     */
    public SpokestackASRResponse(String serverStatus,
                                 String err,
                                 boolean finalResponse,
                                 Hypothesis[] nBest) {
        this.status = serverStatus;
        this.error = err;
        this.isFinal = finalResponse;
        this.hypotheses = nBest;
    }

    /**
     * Data class for a single ASR hypothesis.
     */
    public static class Hypothesis {

        /**
         * text of the hypothesis.
         */
        public final String transcript;

        /**
         * the model's confidence in the hypothesis.
         */
        public final Float confidence;

        /**
         * Create a new hypothesis. Used for testing.
         * @param text the transcript
         * @param conf the model's confidence
         */
        public Hypothesis(String text, Float conf) {
            this.transcript = text;
            this.confidence = conf;
        }
    }
}
