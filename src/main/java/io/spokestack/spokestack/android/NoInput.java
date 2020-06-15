package io.spokestack.spokestack.android;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechInput;

import java.nio.ByteBuffer;

/**
 * An empty input class that does not read from an audio source. Used as a
 * placeholder in the pipeline when no wakeword is needed, and another component
 * needs to control the microphone to perform ASR.
 */
public final class NoInput implements SpeechInput {

    /**
     * initializes a new empty SpeechInput.
     *
     * @param config speech pipeline configuration
     */
    public NoInput(SpeechConfig config) {
    }

    /**
     * simulates reading an frame. actually does nothing.
     *
     * @param context the current speech context
     * @param frame   the frame buffer to fill
     */
    public void read(SpeechContext context, ByteBuffer frame) {
    }

    @Override
    public void close() {
    }
}
