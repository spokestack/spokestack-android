package com.pylon.spokestack;

import java.nio.ByteBuffer;

/**
 * speech input interface.
 *
 * <p>
 * This is the audio input interface to the SpokeStack framework. Implementors
 * must fill the specified audio frame buffer to capacity, based on the
 * configured sample size and frame size.
 * </p>
 *
 * <p>
 * To be used in a speech pipeline, an implementing class must also provide
 * a constructor that accepts a {@link SpeechConfig} instance.
 * </p>
 */
public interface SpeechInput extends AutoCloseable {
    /**
     * reads a set of samples into a frame buffer.
     * @param frame the frame buffer to fill
     * @throws Exception on error
     */
    void read(ByteBuffer frame) throws Exception;
}
