package io.spokestack.spokestack;

import io.spokestack.spokestack.tts.SSML;

/**
 * Text-to-speech service interface.
 *
 * <p>
 * This is the interface for communicating with external TTS services.
 * Implementers receive text to synthesize and must interact with their service
 * of choice, sending the resulting audio URL to any specified listeners.
 * </p>
 *
 * <p>
 * To be used in a speech pipeline, an implementing class must provide a
 * constructor that accepts a {@link ComponentConfig} instance.
 * </p>
 *
 * @see TTSComponent
 */
public interface TTSService extends AutoCloseable, TTSComponent {
    /**
     * Synthesizes a piece of text, dispatching the synthesis request's result
     * to any registered listeners.
     *
     * @param text the text to synthesize
     * @throws Exception on error
     */
    void synthesize(CharSequence text) throws Exception;

    /**
     * Synthesizes a piece of SSML, dispatching the synthesis request's result
     * to any registered listeners.
     *
     * @param ssml the SSML to synthesize
     * @throws Exception on error
     */
    void synthesize(SSML ssml) throws Exception;
}
