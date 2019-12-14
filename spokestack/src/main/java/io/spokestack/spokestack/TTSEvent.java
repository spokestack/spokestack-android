package io.spokestack.spokestack;

import java.net.URI;
import java.util.Map;

/**
 * A wrapper for events in the TTS subsystem. TTS events include responses from
 * external voice synthesis services and media events from player components.
 */
public final class TTSEvent {

    public enum Type {
        /**
         * A synthesis request has completed, and an audio URL has been received
         * from the external service.
         */
        AUDIO_AVAILABLE,

        /**
         * An error has occurred during synthesis or output.
         */
        ERROR
    }

    public final Type type;
    private Map<String, Object> metadata;
    private URI audioUri;
    private Throwable error;

    /**
     * Create an event with the specified type containing no other data.
     * @param type The event type.
     */
    public TTSEvent(Type type) {
        this.type = type;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public URI getAudioUri() {
        return audioUri;
    }

    public void setAudioUri(URI audioUri) {
        this.audioUri = audioUri;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

}
