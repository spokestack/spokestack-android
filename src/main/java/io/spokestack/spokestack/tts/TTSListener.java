package io.spokestack.spokestack.tts;

import androidx.annotation.NonNull;

/**
 * Text-to-speech event callback interface. Response listeners are called when a
 * TTS service returns an audio URL that can be played by a {@link
 * io.spokestack.spokestack.SpeechOutput SpeechOutput} component or system media
 * player, or when such a service returns an error.
 */
public interface TTSListener {

    /**
     * A notification that a TTS event has occurred. This can represent a
     * response from a third-party TTS service, a player event from the output
     * component, or an error from either component.
     *
     * @param event The event from the TTS system.
     */
    void eventReceived(@NonNull TTSEvent event);

}
