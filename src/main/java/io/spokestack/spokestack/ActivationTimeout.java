package io.spokestack.spokestack;

import java.nio.ByteBuffer;

/**
 * Speech recognition activation timer.
 *
 * <p>
 * This component manages the timeout for pipeline activation (the period of
 * time in which the pipeline is actively listening and sending speech through a
 * speech recognition provider).
 * </p>
 *
 * <p>
 * The pipeline can be activated via a trigger component or manually; it will
 * remain active for a minimum amount of time, after which it will be
 * deactivated when the user stops speaking, or a timeout occurs. Both the
 * minimum and maximum activation times are configurable, via the following
 * properties:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>active-min</b> (integer): the minimum length of an
 *      activation, in milliseconds, used to ignore a VAD deactivation after
 *      the manual activation
 *   </li>
 *   <li>
 *      <b>active-max</b> (integer): the maximum length of an
 *      activation, in milliseconds, used to time out the activation
 *   </li>
 * </ul>
 *
 */
public final class ActivationTimeout implements SpeechProcessor {
    private static final int DEFAULT_ACTIVE_MIN = 500;
    private static final int DEFAULT_ACTIVE_MAX = 5000;

    private final int minActive;
    private final int maxActive;

    private boolean isSpeech;
    private int activeLength;

    /**
     * Constructs a new timeout component.
     * @param config the pipeline configuration
     */
    public ActivationTimeout(SpeechConfig config) {
        int frameWidth = config.getInteger("frame-width");
        this.minActive = config.getInteger("active-min", DEFAULT_ACTIVE_MIN)
              / frameWidth;
        this.maxActive = config.getInteger("active-max", DEFAULT_ACTIVE_MAX)
              / frameWidth;
    }

    @Override
    public void process(SpeechContext context, ByteBuffer buffer) {
        boolean vadFall = this.isSpeech && !context.isSpeech();
        this.isSpeech = context.isSpeech();
        if (context.isActive() && ++this.activeLength > this.minActive) {
            if (vadFall || this.activeLength > this.maxActive) {
                deactivate(context);
            }
        }
    }

    private void deactivate(SpeechContext context) {
        reset();
        context.setActive(false);
    }

    @Override
    public void reset() {
        close();
    }

    @Override
    public void close() {
        this.activeLength = 0;
    }
}
