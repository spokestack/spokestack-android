package com.pylon.spokestack.libfvad;

import java.util.Map;
import java.nio.ByteBuffer;

import com.pylon.spokestack.SpeechContext;

/**
 * Voice Activity Detection (VAD) pipeline component
 *
 * <p>
 * VADTrigger is a speech pipeline component that implements Voice Activity
 * Detection (VAD) using the libfvad native component. The detector processes
 * each frame and sets the speech context to active/inactive based on the
 * results of the VAD algorithm. The libfvad implementation is based on
 * the webrtc VAD in the Chromium browser. It supports 16-bit PCM samples.
 * </p>
 *
 * <p>
 * This pipeline component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>sample-rate</b> (int): audio sample rate, in Hz
 *      (supports 8000/16000/32000/48000Hz)
 *   </li>
 *   <li>
 *      <b>frame-width</b> (int): audio frame width, in ms
 *      (supports 10/20/30ms)
 *   </li>
 *   <li>
 *     <b>vad-mode</b> (string): detector mode, one of the following:
 *     <ul>
 *       <li><b>quality</b>: highest recall</li>
 *       <li><b>low-bitrate</b>: higher recall</li>
 *       <li><b>aggressive</b>: higher precision</li>
 *       <li><b>very-aggressive</b>: highest precision</li>
 *     </ul>
 *   </li>
 *   <li>
 *     <b>vad-rise-delay</b> (int): rising-edge detection run length, in ms;
 *     this value determines how many positive samples must be received to
 *     flip the detector to positive
 *   </li>
 *   <li>
 *     <b>vad-fall-delay</b> (int): falling-edge detection run length, in ms;
 *     this value determines how many negative samples must be received to
 *     flip the detector to negative
 *   </li>
 * </ul>
 *
 * <p>
 * The detector uses a simple consecutive value filter to eliminate noisy
 * transitions and allow the recognizer to continue listening during pauses
 * between voiced speech. It raises the "activate" and "deactivate" events
 * on the speech context during edge transitions.
 * </p>
 */
public final class VADTrigger {
    private static final int MODE_QUALITY = 0;
    private static final int MODE_LOW_BITRATE = 1;
    private static final int MODE_AGGRESSIVE = 2;
    private static final int MODE_VERY_AGGRESSIVE = 3;

    private static final int RATE_8KHZ = 8000;
    private static final int RATE_16KHZ = 16000;
    private static final int RATE_32KHZ = 32000;
    private static final int RATE_48KHZ = 48000;

    private static final int WIDTH_10MS = 10;
    private static final int WIDTH_20MS = 20;
    private static final int WIDTH_30MS = 30;

    private final long vadHandle;
    private final int riseLength;
    private final int fallLength;
    private boolean runValue;
    private int runLength;

    /**
     * constructs a new trigger instance.
     * @param config the pipeline configuration map
     */
    public VADTrigger(Map<String, Object> config) {
        // decode the sample rate
        int rate = (Integer) config.get("sample-rate");
        switch (rate) {
            case RATE_8KHZ: break;
            case RATE_16KHZ: break;
            case RATE_32KHZ: break;
            case RATE_48KHZ: break;
            default: throw new IllegalArgumentException("sample-rate");
        }

        // validate the frame width
        int frameWidth = (Integer) config.get("frame-width");
        switch (frameWidth) {
            case WIDTH_10MS: break;
            case WIDTH_20MS: break;
            case WIDTH_30MS: break;
            default: throw new IllegalArgumentException("frame-width");
        }

        // decode the vad mode
        String modeString = (String) config.get("vad-mode");
        int mode = MODE_VERY_AGGRESSIVE;
        if (modeString == "quality")
            mode = MODE_QUALITY;
        else if (modeString == "low-bitrate")
            mode = MODE_LOW_BITRATE;
        else if (modeString == "aggressive")
            mode = MODE_AGGRESSIVE;
        else if (modeString == "very-aggressive")
            mode = MODE_VERY_AGGRESSIVE;
        else if (modeString != null)
            throw new IllegalArgumentException("mode");

        // decode the rising/falling edge delay, in ms
        this.riseLength = config.containsKey("vad-rise-delay")
            ? (Integer) config.get("vad-rise-delay") / frameWidth
            : 0;
        this.fallLength = config.containsKey("vad-fall-delay")
            ? (Integer) config.get("vad-fall-delay") / frameWidth
            : 0;

        // initialize the vad
        this.vadHandle = create(mode, rate);
        if (this.vadHandle == 0)
            throw new OutOfMemoryError();
    }

    /**
     * destroys the unmanaged VAD instance.
     */
    @Override
    protected void finalize() throws Throwable {
        if (this.vadHandle != 0)
            destroy(this.vadHandle);
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param frame   the audio frame to detect
     */
    public void process(SpeechContext context, ByteBuffer frame) {
        // frame detection
        int result = process(this.vadHandle, frame, frame.capacity());
        if (result < 0)
            throw new IllegalStateException();

        // edge filtering
        boolean rawValue = result > 0;
        if (rawValue == this.runValue)
            this.runLength++;
        else {
            this.runValue = rawValue;
            this.runLength = 1;
        }

        // edge triggering
        if (this.runValue != context.isActive()) {
            if (this.runValue && this.runLength >= this.riseLength) {
                context.setActive(true);
                context.dispatch(SpeechContext.Event.ACTIVATE);
            }
            if (!this.runValue && this.runLength >= this.fallLength) {
                context.setActive(false);
                context.dispatch(SpeechContext.Event.DEACTIVATE);
            }
        }
    }

    //-----------------------------------------------------------------------
    // native interface
    //-----------------------------------------------------------------------
    static {
        System.loadLibrary("spokestack");
    }

    native long create(int mode, int rate);
    native void destroy(long vad);
    native int process(long vad, ByteBuffer buffer, int length);
}
