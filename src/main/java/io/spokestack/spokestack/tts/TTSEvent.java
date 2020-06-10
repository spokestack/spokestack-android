package io.spokestack.spokestack.tts;

/**
 * A wrapper for events in the TTS subsystem. TTS events include responses from
 * external voice synthesis services and media events from player components.
 */
public final class TTSEvent {

    /**
     * Enum representing the type of event being dispatched.
     */
    public enum Type {
        /**
         * A synthesis request has completed, and an audio URL has been received
         * from the external service.
         *
         * Note that audio synthesized by Spokestack must be played or
         * downloaded within 30 seconds of URL generation, or it will become
         * inaccessible.
         */
        AUDIO_AVAILABLE,

        /**
         * The Spokestack-managed media player has finished playing all queued
         * prompts.
         */
        PLAYBACK_COMPLETE,

        /**
         * An error has occurred during synthesis or output.
         */
        ERROR
    }

    /**
     * The event type.
     */
    public final Type type;
    private AudioResponse ttsResponse;
    private Throwable ttsError;

    /**
     * Create an event with the specified type containing no other data.
     *
     * @param eventType The event type.
     */
    public TTSEvent(Type eventType) {
        this.type = eventType;
    }

    /**
     * Get the response data containing synthesized audio.
     *
     * @return The TTS synthesis response.
     */
    public AudioResponse getTtsResponse() {
        return ttsResponse;
    }

    /**
     * Attach response data containing the location of synthesized audio.
     *
     * @param response A response from a TTS service.
     */
    public void setTtsResponse(AudioResponse response) {
        this.ttsResponse = response;
    }

    /**
     * Get the error represented by this event.
     *
     * @return An error that occurred in the TTS subsystem.
     */
    public Throwable getError() {
        return ttsError;
    }

    /**
     * Set the error represented by this event.
     *
     * @param error An error that occurred in the TTS subsystem.
     */
    public void setError(Throwable error) {
        this.ttsError = error;
    }

}
