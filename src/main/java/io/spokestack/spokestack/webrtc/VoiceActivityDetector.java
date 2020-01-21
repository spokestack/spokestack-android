package io.spokestack.spokestack.webrtc;

import java.nio.ByteBuffer;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.SpeechContext;

/**
 * Voice Activity Detection (VAD) pipeline component
 *
 * <p>
 * VoiceActivityDetector is a speech pipeline component that implements Voice
 * Activity Detection (VAD) using the webrtc native component. The detector
 * processes each frame and sets the speech context to speech/nonspeech based
 * on the results of the VAD algorithm. The VAD implementation is based on
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
 * transitions.
 * </p>
 */
public class VoiceActivityDetector implements SpeechProcessor {
    /** default voice detection mode (high precision). */
    public static final String DEFAULT_MODE = "very-aggressive";

    private static final int DEFAULT_FALL = 500;

    private static final int MODE_QUALITY = 0;
    private static final int MODE_LOW_BITRATE = 1;
    private static final int MODE_AGGRESSIVE = 2;
    private static final int MODE_VERY_AGGRESSIVE = 3;

    private final int rate;
    private final long vadHandle;
    private final int riseLength;
    private final int fallLength;
    private boolean runValue;
    private int runLength;

    /**
     * constructs a new trigger instance.
     * @param config the pipeline configuration instance
     */
    public VoiceActivityDetector(SpeechConfig config) {
        // decode the sample rate
        this.rate = config.getInteger("sample-rate");
        switch (this.rate) {
            case 8000: break;
            case 16000: break;
            case 32000: break;
            case 48000: break;
            default: throw new IllegalArgumentException("sample-rate");
        }

        // validate the frame width
        int frameWidth = config.getInteger("frame-width");
        switch (frameWidth) {
            case 10: break;
            case 20: break;
            case 30: break;
            default: throw new IllegalArgumentException("frame-width");
        }

        // decode the vad mode
        String modeString = config.getString("vad-mode", DEFAULT_MODE);
        int mode = MODE_VERY_AGGRESSIVE;
        if (modeString.equals("quality"))
            mode = MODE_QUALITY;
        else if (modeString.equals("low-bitrate"))
            mode = MODE_LOW_BITRATE;
        else if (modeString.equals("aggressive"))
            mode = MODE_AGGRESSIVE;
        else if (modeString.equals("very-aggressive"))
            mode = MODE_VERY_AGGRESSIVE;
        else
            throw new IllegalArgumentException("mode");

        // decode the rising/falling edge delay, in ms
        this.riseLength = config.getInteger("vad-rise-delay", 0) / frameWidth;
        this.fallLength = config.getInteger("vad-fall-delay", DEFAULT_FALL)
              / frameWidth;

        // initialize the vad
        this.vadHandle = create(mode);
        if (this.vadHandle == 0)
            throw new OutOfMemoryError();
    }

    /**
     * destroys the unmanaged VAD instance.
     */
    public void close() {
        destroy(this.vadHandle);
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param frame   the audio frame to detect
     */
    public void process(SpeechContext context, ByteBuffer frame) {
        // frame detection
        int result = process(
            this.vadHandle,
            this.rate,
            frame,
            frame.capacity());
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
        if (this.runValue != context.isSpeech()) {
            if (this.runValue && this.runLength >= this.riseLength) {
                context.setSpeech(true);
                context.traceInfo("vad: true");
            }
            if (!this.runValue && this.runLength >= this.fallLength) {
                context.setSpeech(false);
                context.traceInfo("vad: false");
            }
        }
    }

    //-----------------------------------------------------------------------
    // native interface
    //-----------------------------------------------------------------------
    static {
        System.loadLibrary("spokestack-android");
    }

    native long create(int mode);
    native void destroy(long vad);
    native int process(long vad, int fs, ByteBuffer buffer, int length);
}
