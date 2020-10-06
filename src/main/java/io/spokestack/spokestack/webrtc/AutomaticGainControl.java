package io.spokestack.spokestack.webrtc;

import java.nio.ByteBuffer;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.util.EventTracer;

/**
 * Automatic Gain Control (AGC) pipeline component
 *
 * <p>
 * The AGC amplifies/attenuates audio samples to maintain a configured
 * peak amplitude, in dBFS (decibels full-scale). The gain controller is
 * implemented by the native webrtc framework and modifies the audio buffer
 * in place.
 * </p>
 *
 * <p>
 * This pipeline component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>sample-rate</b> (int): audio sample rate, in Hz
 *      (supports 8000/16000/32000Hz)
 *   </li>
 *   <li>
 *      <b>frame-width</b> (int): audio frame width, in ms
 *      (supports 10/20ms)
 *   </li>
 *   <li>
 *     <b>agc-target-level-dbfs</b> (int): target peak audio level, in -dBFS
 *     for example, to maintain a peak of -9dBFS, configure a value of 9
 *   </li>
 *   <li>
 *     <b>agc-compression-gain-db</b> (int): dynamic range compression rate,
 *     in dB
 *   </li>
 * </ul>
 *
 */
public class AutomaticGainControl implements SpeechProcessor {
    /** default target peak amplitude, in dBFS. */
    public static final int DEFAULT_TARGET_LEVEL_DBFS = 3;
    /** default compression gain, in dB. */
    public static final int DEFAULT_COMPRESSION_GAIN_DB = 15;

    // native agc structure handle
    private final long agcHandle;

    // controller output levels and counters, for tracing
    private final int maxCounter;
    private double level;
    private int counter;

    /**
     * constructs a new AGC instance.
     * @param config the pipeline configuration instance
     */
    public AutomaticGainControl(SpeechConfig config) {
        // decode and validate the sample rate
        int rate = config.getInteger("sample-rate");
        switch (rate) {
            case 8000: break;
            case 16000: break;
            case 32000: break;
            default: throw new IllegalArgumentException("sample-rate");
        }

        // decode and validate the frame width
        int frameWidth = config.getInteger("frame-width");
        switch (frameWidth) {
            case 10: break;
            case 20: break;
            default: throw new IllegalArgumentException("frame-width");
        }

        // perf trace every second
        this.maxCounter = 1000 / frameWidth;

        // decode the agc parameters
        int targetLeveldBFS = config.getInteger(
            "agc-target-level-dbfs",
            DEFAULT_TARGET_LEVEL_DBFS);

        int compressionGaindB = config.getInteger(
            "agc-compression-gain-db",
            DEFAULT_COMPRESSION_GAIN_DB);

        // create the native agc context
        this.agcHandle = create(
            rate,
            targetLeveldBFS,
            compressionGaindB,
            false);
        if (this.agcHandle == 0)
            throw new OutOfMemoryError();
    }

    /**
     * destroys the unmanaged AGC instance.
     */
    public void close() {
        destroy(this.agcHandle);
    }

    @Override
    public void reset() {
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param frame   the audio frame to detect
     */
    public void process(SpeechContext context, ByteBuffer frame) {
        // run the native gain controller,
        // which will update the frame buffer
        int result = process(this.agcHandle, frame, frame.capacity());
        if (result < 0)
            throw new IllegalStateException();

        // trace the amplification levels
        if (context.canTrace(EventTracer.Level.PERF)) {
            // measure the updated sample RMS dBFS
            // maintain a running mean of the levels
            double frameLevel = rms(frame);
            this.counter++;
            this.level += (frameLevel - this.level) / this.counter;

            // trace them once per tracing interval
            this.counter %= this.maxCounter;
            if (this.counter == 0)
                context.tracePerf("agc: %.4f", this.level);
        }
    }

    private double rms(ByteBuffer signal) {
        double sum = 0;
        int count = 0;

        signal.rewind();
        while (signal.hasRemaining()) {
            double sample = (double) signal.getShort() / Short.MAX_VALUE;
            sum += sample * sample;
            count++;
        }

        return 20 * Math.log10(Math.max(Math.sqrt(sum / count), 2e-5) / 2e-5);
    }

    //-----------------------------------------------------------------------
    // native interface
    //-----------------------------------------------------------------------
    static {
        System.loadLibrary("spokestack-android");
    }

    native long create(
        int rate,
        int targetLeveldBFS,
        int compressionGaindB,
        boolean limiterEnable);
    native void destroy(long agc);
    native int process(long agc, ByteBuffer buffer, int length);
}
