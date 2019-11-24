package io.spokestack.spokestack.webrtc;

import java.nio.ByteBuffer;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.SpeechContext;

/**
 * Acoustic Noise Suppressor (ANS) pipeline component
 *
 * <p>
 * AcousticNoiseSuppressor is a speech pipeline component that implements
 * automatic noise separation and suppression. The denoised frames are
 * written back to the frame buffer in-place. The VAD implementation is
 * based on the webrtc noise suppressor in the Chromium browser. It supports
 * 16-bit PCM samples with a frame rate multiple of 10ms.
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
 *     <b>ans-policy</b> (string): noise policy, one of the following:
 *     <ul>
 *       <li><b>mild</b>: mild supression (6dB)</li>
 *       <li><b>medium</b>: medium supression (10dB)</li>
 *       <li><b>aggressive</b>: aggressive supression (15dB)</li>
 *       <li><b>very-aggressive</b>:
 *           very aggressive supression (undocumented)</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class AcousticNoiseSuppressor implements SpeechProcessor {
    private static final int POLICY_MILD = 0;
    private static final int POLICY_MEDIUM = 1;
    private static final int POLICY_AGGRESSIVE = 2;
    private static final int POLICY_VERY_AGGRESSIVE = 3;

    /** default suppressor policy. */
    public static final String DEFAULT_POLICY = "mild";

    // native ans structure handle
    private final long ansHandle;
    private final int frameWidth;

    /**
     * constructs a new suppressor instance.
     * @param config the pipeline configuration instance
     */
    public AcousticNoiseSuppressor(SpeechConfig config) {
        // decode and validate the sample rate
        int rate = config.getInteger("sample-rate");
        switch (rate) {
            case 8000: break;
            case 16000: break;
            case 32000: break;
            default: throw new IllegalArgumentException("sample-rate");
        }

        // decode and validate the frame width
        // this must be a multiple 10ms of audio,
        // which is the only frame size supported by the suppressor
        this.frameWidth = rate * 10 / 1000;
        if (config.getInteger("frame-width") % 10 != 0)
            throw new IllegalArgumentException("frame-width");

        // decode and validate the policy
        String policyString = config.getString("ans-policy", DEFAULT_POLICY);
        int policy = POLICY_VERY_AGGRESSIVE;
        if (policyString.equals("mild"))
            policy = POLICY_MILD;
        else if (policyString.equals("medium"))
            policy = POLICY_MEDIUM;
        else if (policyString.equals("aggressive"))
            policy = POLICY_AGGRESSIVE;
        else if (policyString.equals("very-aggressive"))
            policy = POLICY_VERY_AGGRESSIVE;
        else
            throw new IllegalArgumentException("policy");

        // create the native suppressor context
        this.ansHandle = create(rate, policy);
        if (this.ansHandle == 0)
            throw new OutOfMemoryError();
    }

    /**
     * destroys the unmanaged ans instance.
     */
    public void close() {
        destroy(this.ansHandle);
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param frame   the audio frame to detect
     */
    public void process(SpeechContext context, ByteBuffer frame) {
        // compute the frame size, in bytes
        int frameSize = this.frameWidth * 2;
        if (frame.capacity() % frameSize != 0)
            throw new IllegalStateException();

        // run the native noise suppressor for each suppressor frame,
        // which will update the frame buffer
        for (int offset = 0; offset < frame.capacity(); offset += frameSize) {
            int result = process(this.ansHandle, frame, offset);
            if (result < 0)
                throw new IllegalStateException();
        }
    }

    //-----------------------------------------------------------------------
    // native interface
    //-----------------------------------------------------------------------
    static {
        System.loadLibrary("spokestack");
    }

    native long create(int rate, int policy);
    native void destroy(long ans);
    native int process(long ans, ByteBuffer buffer, int offset);
}
