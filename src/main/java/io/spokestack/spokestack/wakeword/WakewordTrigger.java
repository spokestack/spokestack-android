package io.spokestack.spokestack.wakeword;

import io.spokestack.spokestack.RingBuffer;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.tensorflow.TensorflowModel;
import org.jtransforms.fft.FloatFFT_1D;

import java.nio.ByteBuffer;


/**
 * wakeword Detection pipeline component
 *
 * <p>
 * WakewordTrigger is a speech pipeline component that provides wakeword
 * detection for activating downstream components. It uses a Tensorflow-Lite
 * binary classifier to detect keyword phrases. Once a wakeword phrase is
 * detected, the pipeline is activated. The pipeline remains active until the
 * user stops talking or the activation timeout is reached.
 * </p>
 *
 * <p>
 * The incoming raw audio signal is first normalized and then converted to
 * the magnitude Short-Time Fourier Transform (STFT) representation over a
 * hopped sliding window. This linear spectrogram is then converted to a
 * mel spectrogram via a "filter" Tensorflow model. These mel frames are
 * batched together into a sliding window.
 * </p>
 *
 * <p>
 * The mel spectrogram represents the features to be passed to the
 * autoregressive encoder (usually an rnn or crnn), which is implemented in
 * an "encode" Tensorflow model. This encoder outputs an encoded vector and a
 * state vector. The encoded vectors are batched together into a sliding
 * window for classification, and the state vector is used to perform the
 * running autoregressive transduction over the mel frames.
 * </p>
 *
 * <p>
 * The "detect" Tensorflow model takes the encoded sliding window and outputs
 * a single posterior value in the range [0, 1]. Values closer to 1 indicate
 * a detected keyword phrase, values closer to 0 indicate non-keyword speech.
 * This classifier is commonly implemented as an attention mechanism over the
 * encoder window.
 * </p>
 *
 * <p>
 * The detector's outputs are then compared against a configured threshold,
 * in order to determine whether to activate the pipeline. If the posterior
 * is greater than the threshold, the activation occurs.
 * </p>
 *
 * <p>
 * Activations have configurable minimum/maximum lengths. The minimum length
 * prevents the activation from being aborted if the user pauses after saying
 * the wakeword (which untriggers the VAD). The maximum activation length
 * allows the activation to timeout if the user doesn't say anything after
 * saying the wakeword.
 * </p>
 *
 * <p>
 * The wakeword detector can be used in a multi-turn dialogue system. In
 * such an environment, the user is not expected to say the wakeword during
 * each turn. Therefore, an application can manually activate the pipeline
 * by calling <b>setActive</b> (after a system turn), and the wakeword
 * detector will apply its minimum/maximum activation lengths to control
 * the duration of the activation.
 * </p>
 *
 * <p>
 * This pipeline component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>wake-filter-path</b> (string, required): file system path to the
 *      "filter" Tensorflow-Lite model, which is used to calculate a mel
 *      spectrogram frame from the linear STFT; its inputs should be shaped
 *      [fft-width], and its outputs [mel-width]
 *   </li>
 *   <li>
 *      <b>wake-encode-path</b> (string, required): file system path to the
 *      "encode" Tensorflow-Lite model, which is used to perform each
 *      autoregressive step over the mel frames; its inputs should be shaped
 *      [mel-length, mel-width], and its outputs [encode-width], with an
 *      additional state input/output shaped [state-width]
 *   </li>
 *   <li>
 *      <b>wake-detect-path</b> (string, required): file system path to the
 *      "detect" Tensorflow-Lite model; its inputs shoudld be shaped
 *      [encode-length, encode-width], and its outputs [1]
 *   </li>
 *   <li>
 *      <b>rms-target</b> (double): the desired linear Root Mean Squared (RMS)
 *      signal energy, which is used for signal normalization and should be
 *      tuned to the RMS target used during training
 *   </li>
 *   <li>
 *      <b>rms-alpha</b> (double): the Exponentially-Weighted Moving Average
 *      (EWMA) update rate for the current RMS signal energy (0 for no
 *      RMS normalization)
 *   </li>
 *   <li>
 *      <b>pre-emphasis</b> (double): the pre-emphasis filter weight to apply
 *      to the normalized audio signal (0 for no pre-emphasis)
 *   </li>
 *   <li>
 *      <b>fft-window-size</b> (integer): the size of the signal window used
 *      to calculate the STFT, in number of samples - should be a power of
 *      2 for maximum efficiency
 *   </li>
 *   <li>
 *      <b>fft-window-type</b> (string): the name of the windowing function
 *      to apply to each audio frame before calculating the STFT; currently
 *      the "hann" window is supported
 *   </li>
 *   <li>
 *      <b>fft-hop-length</b> (integer): the length of time to skip each
 *      time the overlapping STFT is calculated, in milliseconds
 *   </li>
 *   <li>
 *      <b>mel-frame-length</b> (integer): the length of the mel spectrogram
 *      used as an input to the encoder, in milliseconds
 *   </li>
 *   <li>
 *      <b>mel-frame-width</b> (integer): the size of each mel spectrogram
 *      frame, in number of filterbank components
 *   </li>
 *   <li>
 *      <b>wake-encode-length</b> (integer): the length of the sliding
 *      window of encoder output used as an input to the classifier, in
 *      milliseconds
 *   </li>
 *   <li>
 *      <b>wake-encode-width</b> (integer): the size of the encoder output,
 *      in vector units
 *   </li>
 *   <li>
 *      <b>wake-state-width</b> (integer): the size of the encoder state,
 *      in vector units (defaults to wake-encode-width)
 *   </li>
 *   <li>
 *      <b>wake-threshold</b> (double): the threshold of the classifier's
 *      posterior output, above which the trigger activates the pipeline,
 *      in the range [0, 1]
 *   </li>
 * </ul>
 */
public final class WakewordTrigger implements SpeechProcessor {
    /** the hann fft-window-type.  */
    public static final String FFT_WINDOW_TYPE_HANN = "hann";

    /** default fft-window-type configuration value. */
    public static final String DEFAULT_FFT_WINDOW_TYPE = FFT_WINDOW_TYPE_HANN;
    /** default rms-target configuration value. */
    public static final float DEFAULT_RMS_TARGET = 0.08f;
    /** default rms-alpha configuration value. */
    public static final float DEFAULT_RMS_ALPHA = 0.0f;
    /** default pre-emphasis configuration value. */
    public static final float DEFAULT_PRE_EMPHASIS = 0.0f;
    /** default fft-window-size configuration value. */
    public static final int DEFAULT_FFT_WINDOW_SIZE = 512;
    /** default fft-hop-length configuration value. */
    public static final int DEFAULT_FFT_HOP_LENGTH = 10;
    /** default mel-frame-length configuration value. */
    public static final int DEFAULT_MEL_FRAME_LENGTH = 10;
    /** default mel-frame-width configuration value. */
    public static final int DEFAULT_MEL_FRAME_WIDTH = 40;
    /** default wake-encode-length configuration value. */
    public static final int DEFAULT_WAKE_ENCODE_LENGTH = 1000;
    /** default wake-encode-width configuration value. */
    public static final int DEFAULT_WAKE_ENCODE_WIDTH = 128;
    /** default wake-threshold value. */
    public static final float DEFAULT_WAKE_THRESHOLD = 0.5f;

    // voice activity detection
    private boolean isSpeech;

    // pipeline timeout sensitivity
    private boolean isActive;

    // audio signal normalization and pre-emphasis
    private final float rmsTarget;
    private final float rmsAlpha;
    private final float preEmphasis;
    private float rmsValue;
    private float prevSample;

    // stft/mel filterbank configuration
    private final FloatFFT_1D fft;
    private final float[] fftWindow;
    private final float[] fftFrame;
    private final int hopLength;
    private final int melWidth;

    // encoder configuration
    private final int encodeWidth;

    // sliding window buffers
    private final RingBuffer sampleWindow;
    private final RingBuffer frameWindow;
    private final RingBuffer encodeWindow;

    // tensorflow mel filtering and classifier models
    private final TensorflowModel filterModel;
    private final TensorflowModel encodeModel;
    private final TensorflowModel detectModel;

    // wakeword activation management
    private final float posteriorThreshold;
    private float posteriorMax;

    /**
     * constructs a new trigger instance.
     * @param config the pipeline configuration instance
     */
    public WakewordTrigger(SpeechConfig config) {
        this(config, new TensorflowModel.Loader());
    }

    /**
     * constructs a new trigger instance, for testing.
     * @param config the pipeline configuration instance
     * @param loader tensorflow model loader
     */
    public WakewordTrigger(
            SpeechConfig config,
            TensorflowModel.Loader loader) {
        // fetch signal normalization config
        this.rmsTarget = (float) config
            .getDouble("rms-target", (double) DEFAULT_RMS_TARGET);
        this.rmsAlpha = (float) config
            .getDouble("rms-alpha", (double) DEFAULT_RMS_ALPHA);
        this.preEmphasis = (float) config
            .getDouble("pre-emphasis", (double) DEFAULT_PRE_EMPHASIS);
        this.rmsValue = this.rmsTarget;

        // fetch and validate stft/mel spectrogram configuration
        int sampleRate = config
            .getInteger("sample-rate");
        int windowSize = config
            .getInteger("fft-window-size", DEFAULT_FFT_WINDOW_SIZE);
        this.hopLength = config
            .getInteger("fft-hop-length", DEFAULT_FFT_HOP_LENGTH)
            * sampleRate / 1000;
        String windowType = config
            .getString("fft-window-type", DEFAULT_FFT_WINDOW_TYPE);
        if (windowSize % 2 != 0)
            throw new IllegalArgumentException("fft-window-size");
        int melLength = config
            .getInteger("mel-frame-length", DEFAULT_MEL_FRAME_LENGTH)
            * sampleRate / 1000 / this.hopLength;
        this.melWidth = config
            .getInteger("mel-frame-width", DEFAULT_MEL_FRAME_WIDTH);

        // allocate the stft window and FFT/frame buffer
        if (windowType.equals(FFT_WINDOW_TYPE_HANN))
            this.fftWindow = hannWindow(windowSize);
        else
            throw new IllegalArgumentException("fft-window-type");

        this.fft = new FloatFFT_1D(windowSize);
        this.fftFrame = new float[windowSize];

        // fetch and validate encoder configuration
        int encodeLength = config
            .getInteger("wake-encode-length", DEFAULT_WAKE_ENCODE_LENGTH)
            * sampleRate / 1000 / this.hopLength;
        this.encodeWidth = config
            .getInteger("wake-encode-width", DEFAULT_WAKE_ENCODE_WIDTH);
        int stateWidth = config
            .getInteger("wake-state-width", this.encodeWidth);

        // allocate sliding windows
        // fill all buffers (except samples) with zero, in order to
        // minimize detection delay caused by buffering
        this.sampleWindow = new RingBuffer(windowSize);
        this.frameWindow = new RingBuffer(melLength * this.melWidth);
        this.encodeWindow = new RingBuffer(encodeLength * this.encodeWidth);

        this.frameWindow.fill(0);
        this.encodeWindow.fill(-1);

        // load the tensorflow-lite models
        this.filterModel = loader
            .setPath(config.getString("wake-filter-path"))
            .load();
        loader.reset();
        this.encodeModel = loader
            .setPath(config.getString("wake-encode-path"))
            .setStatePosition(1)
            .load();
        loader.reset();
        this.detectModel = loader
            .setPath(config.getString("wake-detect-path"))
            .load();

        // configure the wakeword activation lengths
        this.posteriorThreshold = (float) config
            .getDouble("wake-threshold", (double) DEFAULT_WAKE_THRESHOLD);
    }

    /**
     * releases resources associated with the wakeword detector.
     * @throws Exception on error
     */
    public void close() throws Exception {
        this.filterModel.close();
        this.encodeModel.close();
        this.detectModel.close();
    }

    @Override
    public void reset() {
        // empty the sample buffer, so that only contiguous
        // speech samples are written to it
        this.sampleWindow.reset();

        // reset and fill the other buffers,
        // which prevents them from delaying detection
        // the encoder has a tanh nonlinearity, so fill it with -1
        this.frameWindow.reset().fill(0);
        this.encodeWindow.reset().fill(-1);

        // reset the encoder states
        while (this.encodeModel.states().hasRemaining())
            this.encodeModel.states().putFloat(0);

        // reset the maximum posterior
        this.posteriorMax = 0;
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param buffer  the audio frame to detect
     * @throws Exception on error
     */
    public void process(SpeechContext context, ByteBuffer buffer)
            throws Exception {
        // detect speech deactivation edges for wakeword deactivation
        boolean vadFall = this.isSpeech && !context.isSpeech();
        boolean deactivate = this.isActive && !context.isActive();
        this.isSpeech = context.isSpeech();
        this.isActive = context.isActive();

        // always reset detector state on vad or pipeline deactivation
        if (vadFall || deactivate) {
            if (vadFall && !context.isActive())
                trace(context);
            reset();
        }

        if (!context.isActive()) {
            // run the current frame through the detector pipeline
            // activate if a keyword phrase was detected
            sample(context, buffer);
        }
    }

    private void sample(SpeechContext context, ByteBuffer buffer) {
        // update the rms normalization factors
        // maintain an ewma of the rms signal energy for speech samples
        if (context.isSpeech() && this.rmsAlpha > 0)
            this.rmsValue =
                this.rmsAlpha * rms(buffer)
                + (1 - this.rmsAlpha) * this.rmsValue;

        // process all samples in the frame
        buffer.rewind();
        while (buffer.hasRemaining()) {
            // normalize and clip the 16-bit sample to the target rms energy
            float sample = (float) buffer.getShort() / Short.MAX_VALUE;
            sample = sample * this.rmsTarget / this.rmsValue;
            sample = Math.max(-1f, Math.min(sample, 1f));

            // run a pre-emphasis filter to balance high frequencies
            // and eliminate any dc energy
            float nextSample = sample;
            sample -= this.preEmphasis * this.prevSample;
            this.prevSample = nextSample;

            // process the sample
            // . write it to the sample sliding window
            // . run the remainder of the detection pipeline if speech
            // . advance the sample sliding window
            this.sampleWindow.write(sample);
            if (this.sampleWindow.isFull()) {
                if (context.isSpeech())
                    analyze(context);
                this.sampleWindow.rewind().seek(this.hopLength);
            }
        }
    }

    private void analyze(SpeechContext context) {
        // apply the windowing function to the current sample window
        for (int i = 0; i < this.fftFrame.length; i++)
            this.fftFrame[i] = this.sampleWindow.read() * this.fftWindow[i];

        // compute the stft
        this.fft.realForward(this.fftFrame);

        filter(context);
    }

    private void filter(SpeechContext context) {
        // decode the FFT outputs into the filter model's inputs
        // . compute the magnitude (abs) of each complex stft component
        // . the first and last stft components contain only real parts
        //   and are stored in the first two positions of the stft output
        // . the remaining components contain real/imaginary parts
        this.filterModel.inputs(0).rewind();
        this.filterModel.inputs(0).putFloat(this.fftFrame[0]);
        for (int i = 1; i < this.fftFrame.length / 2; i++) {
            float re = this.fftFrame[i * 2 + 0];
            float im = this.fftFrame[i * 2 + 1];
            float ab = (float) Math.sqrt(re * re + im * im);
            this.filterModel.inputs(0).putFloat(ab);
        }
        this.filterModel.inputs(0).putFloat(this.fftFrame[1]);

        // execute the mel filterbank tensorflow model
        this.filterModel.run();

        // copy the current mel frame into the mel window
        this.frameWindow.rewind().seek(this.melWidth);
        while (this.filterModel.outputs(0).hasRemaining()) {
            this.frameWindow.write(
                  this.filterModel.outputs(0).getFloat());
        }

        encode(context);
    }

    private void encode(SpeechContext context) {
        // transfer the mel filterbank window to the encoder model's inputs
        this.frameWindow.rewind();
        this.encodeModel.inputs(0).rewind();
        while (!this.frameWindow.isEmpty()) {
            this.encodeModel.inputs(0).putFloat(this.frameWindow.read());
        }

        // run the encoder tensorflow model
        this.encodeModel.run();

        // copy the encoder output into the encode window
        this.encodeWindow.rewind().seek(this.encodeWidth);
        while (this.encodeModel.outputs(0).hasRemaining()) {
            this.encodeWindow.write(
                  this.encodeModel.outputs(0).getFloat());
        }

        detect(context);
    }

    private void detect(SpeechContext context) {
        // transfer the encoder window to the detector model's inputs
        this.encodeWindow.rewind();
        this.detectModel.inputs(0).rewind();
        while (!this.encodeWindow.isEmpty())
            this.detectModel.inputs(0).putFloat(this.encodeWindow.read());

        // run the classifier tensorflow model
        this.detectModel.run();

        // check the classifier's output and activate
        float posterior = this.detectModel.outputs(0).getFloat();
        if (posterior > this.posteriorMax)
            this.posteriorMax = posterior;
        if (posterior > this.posteriorThreshold)
            activate(context);
    }

    private void activate(SpeechContext context) {
        trace(context);
        context.setActive(true);
    }

    private void trace(SpeechContext context) {
        context.traceInfo(String.format("wake: %f", this.posteriorMax));
    }

    private float[] hannWindow(int len) {
        // https://en.wikipedia.org/wiki/Hann_function
        float[] window = new float[len];
        for (int i = 0; i < len; i++)
            window[i] = (float) Math.pow(Math.sin(Math.PI * i / (len - 1)), 2);
        return window;
    }

    private float rms(ByteBuffer signal) {
        float sum = 0;
        int count = 0;

        signal.rewind();
        while (signal.hasRemaining()) {
            float sample = (float) signal.getShort() / Short.MAX_VALUE;
            sum += sample * sample;
            count++;
        }

        return (float) Math.sqrt(sum / count);
    }
}
