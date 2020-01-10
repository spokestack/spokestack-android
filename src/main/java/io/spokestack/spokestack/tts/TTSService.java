package io.spokestack.spokestack.tts;

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
 * constructor that accepts a {@link io.spokestack.spokestack.SpeechConfig
 * SpeechConfig} instance.
 * </p>
 *
 * @see TTSComponent
 */
public abstract class TTSService extends TTSComponent
      implements AutoCloseable {

    /**
     * Synthesizes a piece of text or SSML, dispatching the result to any
     * registered listeners.
     *
     * @param request The synthesis request data.
     */
    public abstract void synthesize(SynthesisRequest request);
}
