package com.pylon.spokestack;

import java.nio.ByteBuffer;

/**
 * speech pipeline processor interface.
 *
 * <p>
 * This is the interface between the speech pipeline and frame handler
 * components. The pipeline calls each registered processor stage on every
 * frame, passing in the current speech context and audio frame buffer.
 * </p>
 *
 * <p>
 * To be used in a speech pipeline, an implementing class must provide
 * a constructor that accepts a {@link SpeechConfig} instance.
 * </p>
 */
public interface SpeechProcessor extends AutoCloseable {
    /**
     * processes the current speech frame.
     * @param context the current speech context
     * @param frame   the received audio frame
     * @throws Exception on error
     */
    void process(SpeechContext context, ByteBuffer frame) throws Exception;
}
