package io.spokestack.spokestack.asr;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechProcessor;

import java.nio.ByteBuffer;

/**
 * Empty speech recognizer
 *
 * <p>
 * This recognizer is designed for use in profiles that want to skip ASR
 * entirely, dispatching only activate and deactivate events from a wakeword
 * recognizer.
 * </p>
 *
 * <p>
 * Once the wakeword is recognized, this stage allows the pipeline to remain
 * active for a single frame then deactivates it.
 * </p>
 */
public class EmptyRecognizer implements SpeechProcessor {

    private boolean active = false;

    /**
     * initializes a new recognizer instance.
     *
     * @param speechConfig Spokestack speech configuration
     */
    public EmptyRecognizer(SpeechConfig speechConfig) {
        // no configuration necessary
    }

    @Override
    public void process(SpeechContext context, ByteBuffer frame)
          throws Exception {
        // all we want to do is return control to the wakeword component, so
        // simply deactivate the context. this allows multiple wakeword
        // utterances to be recognized in quick succession.
        // we want to leave the context active for one frame, though, so the
        // wakeword trigger has a chance to recognize the activity and reset
        // itself when we deactivate on the following frame; otherwise, we'll
        // get repeated activations as the wakeword trigger fires for multiple
        // frames in a row.
        if (this.active) {
            context.setActive(false);
        }
        this.active = context.isActive();
    }

    @Override
    public void reset() throws Exception {
    }

    @Override
    public void close() throws Exception {
    }

    /**
     * determines whether the recognizer is currently active. used for testing.
     */
    boolean isActive() {
        return this.active;
    }
}
