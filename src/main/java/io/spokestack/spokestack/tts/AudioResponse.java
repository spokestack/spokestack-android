package io.spokestack.spokestack.tts;

import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple data class that represents an HTTP response from a TTS service, with
 * the URI containing the synthesized audio at the top level.
 */
public class AudioResponse {
    private final Map<String, Object> metadata;
    private final Uri uri;

    /**
     * Create a new TTS response containing only the URI containing
     * synthesized audio.
     *
     * @param audioUri The URI at which synthesized audio can be found.
     */
    public AudioResponse(Uri audioUri) {
        this(new HashMap<>(), audioUri);
    }

    /**
     * Create a new TTS response containing only the URI containing
     * synthesized audio.
     *
     * @param responseData Additional response data.
     * @param audioUri The URI at which synthesized audio can be found.
     */
    public AudioResponse(Map<String, Object> responseData, Uri audioUri) {
        this.metadata = responseData;
        this.uri = audioUri;
    }

    /**
     * Get additional data accompanying the synthesized audio.
     *
     * @return The response's metadata.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Get the URI containing synthesized audio.
     *
     * @return The audio URI.
     */
    public Uri getAudioUri() {
        return uri;
    }
}
