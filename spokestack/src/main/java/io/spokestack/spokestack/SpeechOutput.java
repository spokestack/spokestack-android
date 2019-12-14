package io.spokestack.spokestack;

import java.net.URI;

/**
 * Text-to-speech output.
 *
 * <p>
 * This is the audio output interface for the Spokestack framework. Implementers
 * receive an audio URL provided by a TTS service and must interact with a media
 * player to play that audio, pausing the speech pipeline while the resulting
 * audio plays if desired and then resuming it when playback is over.
 * </p>
 *
 * <p>
 * {@code SpeechOutput} implementers are {@link TTSListener}s by definition;
 * thus, if a TTS subsystem has an output class, additional listeners are not
 * required. {@code SpeechOutput} components do publish their own events using
 * the same interface, however, so an app may wish to include a listener
 * component to receive media player events or errors.
 * </p>
 *
 * <p>
 * To be used in a TTS subsystem, an implementing class must provide a
 * constructor that accepts a {@link ComponentConfig} instance.
 * </p>
 *
 * @see TTSComponent
 */
public interface SpeechOutput extends AutoCloseable, TTSComponent, TTSListener {

    /**
     * Plays audio from the specified location.
     *
     * @param audioUri The URI where audio can be found.
     */
    void playAudio(URI audioUri);
}
