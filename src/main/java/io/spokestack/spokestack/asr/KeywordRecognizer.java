package io.spokestack.spokestack.asr;

import io.spokestack.spokestack.RingBuffer;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.tensorflow.TensorflowModel;
import org.jtransforms.fft.FloatFFT_1D;

import java.nio.ByteBuffer;

/**
 * keyword recognition pipeline component
 *
 * <p>
 * KeywordRecognizer is a speech pipeline component that provides the ability
 * to recognize one or more keyword phrases during pipeline activation. Its
 * behavior is similar to the other speech recognizer components, albeit for
 * a limited vocabulary (usually just a few words/phrases).
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
 * a set of independent posterior values in the range [0, 1], one per keyword
 * class.
 * </p>
 *
 * <p>
 * During detection, the highest scoring posterior is chosen as the
 * recognized class, and if its value is higher than the configured
 * threshold, that class is reported to the client through the speech
 * recognition event. Otherwise, a timeout event occurs. Note
 * that the detection model is only run on the frame in which the speech
 * context is deactivated, similar to the end-of-utterance mechanism used by
 * the other speech recognizers.
 * </p>
 *
 * <p>
 * The keyword recognizer can be used as a stand-alone speech recognizer,
 * using the VAD/timeout (or other activator) to manage activations.
 * Alternatively, the recognizer can be used along with a wakeword detector
 * to manage activations, in a two-stage wakeword/recognizer pattern.
 * </p>
 *
 * <p>
 * This pipeline component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>keyword-classes</b> (string, required): comma-separated ordered
 *      list of class names for the keywords; the name corresponding to the
 *      most likely class will be returned in the transcript field when the
 *      recognition event is raised
 *   </li>
 *   <li>
 *      <b>keyword-filter-path</b> (string, required): file system path to the
 *      "filter" Tensorflow-Lite model, which is used to calculate a mel
 *      spectrogram frame from the linear STFT; its inputs should be shaped
 *      [fft-width], and its outputs [mel-width]
 *   </li>
 *   <li>
 *      <b>keyword-encode-path</b> (string, required): file system path to the
 *      "encode" Tensorflow-Lite model, which is used to perform each
 *      autoregressive step over the mel frames; its inputs should be shaped
 *      [mel-length, mel-width], and its outputs [encode-width], with an
 *      additional state input/output shaped [state-width]
 *   </li>
 *   <li>
 *      <b>keyword-detect-path</b> (string, required): file system path to the
 *      "detect" Tensorflow-Lite model; its inputs should be shaped
 *      [encode-length, encode-width], and its outputs [len(classes)]
 *   </li>
 *   <li>
 *      <b>keyword-pre-emphasis</b> (double): the pre-emphasis filter weight
 *      to apply to the audio signal (0 for no pre-emphasis)
 *   </li>
 *   <li>
 *      <b>keyword-fft-window-size</b> (integer): the size of the signal
 *      window used to calculate the STFT, in number of samples - should be a
 *      power of 2 for maximum efficiency
 *   </li>
 *   <li>
 *      <b>keyword-fft-window-type</b> (string): the name of the windowing
 *      function to apply to each audio frame before calculating the STFT;
 *      currently the "hann" window is supported
 *   </li>
 *   <li>
 *      <b>keyword-fft-hop-length</b> (integer): the length of time to skip
 *      each time the overlapping STFT is calculated, in milliseconds
 *   </li>
 *   <li>
 *      <b>keyword-mel-frame-length</b> (integer): the length of the mel
 *      spectrogram used as an input to the encoder, in milliseconds
 *   </li>
 *   <li>
 *      <b>keyword-mel-frame-width</b> (integer): the size of each mel
 *      spectrogram frame, in number of filterbank components
 *   </li>
 *   <li>
 *      <b>keyword-encode-length</b> (integer): the length of the sliding
 *      window of encoder output used as an input to the classifier, in
 *      milliseconds
 *   </li>
 *   <li>
 *      <b>keyword-encode-width</b> (integer): the size of the encoder output,
 *      in vector units
 *   </li>
 *   <li>
 *      <b>keyword-state-width</b> (integer): the size of the encoder state,
 *      in vector units (defaults to keyword-encode-width)
 *   </li>
 *   <li>
 *      <b>keyword-threshold</b> (double): the threshold of the classifier's
 *      posterior output, above which the recognizer raises a recognition
 *      event for the most likely kewyord class, in the range [0, 1]
 *   </li>
 * </ul>
 */
public final class KeywordRecognizer implements SpeechProcessor {
    /** the hann keyword-fft-window-type.  */
    public static final String FFT_WINDOW_TYPE_HANN = "hann";

    /** default keyword-fft-window-type configuration value. */
    public static final String DEFAULT_FFT_WINDOW_TYPE = FFT_WINDOW_TYPE_HANN;
    /** default keyword-pre-emphasis configuration value. */
    public static final float DEFAULT_PRE_EMPHASIS = 0.97f;
    /** default keyword-fft-window-size configuration value. */
    public static final int DEFAULT_FFT_WINDOW_SIZE = 512;
    /** default keyword-fft-hop-length configuration value. */
    public static final int DEFAULT_FFT_HOP_LENGTH = 10;
    /** default keyword-mel-frame-length configuration value. */
    public static final int DEFAULT_MEL_FRAME_LENGTH = 110;
    /** default keyword-mel-frame-width configuration value. */
    public static final int DEFAULT_MEL_FRAME_WIDTH = 40;
    /** default keyword-encode-length configuration value. */
    public static final int DEFAULT_ENCODE_LENGTH = 1000;
    /** default keyword-encode-width configuration value. */
    public static final int DEFAULT_ENCODE_WIDTH = 128;
    /** default recognition threshold value. */
    public static final float DEFAULT_THRESHOLD = 0.5f;

    // keyword class names
    private final String[] classes;

    // audio pre-emphasis
    private final float preEmphasis;
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

    // detection posterior threshold
    private final float threshold;

    // context state
    private boolean isActive;

    /**
     * constructs a new recognizer instance.
     * @param config the pipeline configuration instance
     */
    public KeywordRecognizer(SpeechConfig config) {
        this(config, new TensorflowModel.Loader());
    }

    /**
     * constructs a new recognizer instance, for testing.
     * @param config the pipeline configuration instance
     * @param loader tensorflow model loader
     */
    public KeywordRecognizer(
            SpeechConfig config,
            TensorflowModel.Loader loader) {
        // parse the list of keyword class names
        this.classes = config.getString("keyword-classes").split(",");
        if (this.classes.length < 1)
            throw new IllegalArgumentException("keyword-classes");

        // fetch signal normalization config
        this.preEmphasis = (float) config
            .getDouble("keyword-pre-emphasis", (double) DEFAULT_PRE_EMPHASIS);

        // fetch and validate stft/mel spectrogram configuration
        int sampleRate = config
            .getInteger("sample-rate");
        int windowSize = config
            .getInteger("keyword-fft-window-size", DEFAULT_FFT_WINDOW_SIZE);
        this.hopLength = config
            .getInteger("keyword-fft-hop-length", DEFAULT_FFT_HOP_LENGTH)
            * sampleRate / 1000;
        String windowType = config
            .getString("keyword-fft-window-type", DEFAULT_FFT_WINDOW_TYPE);
        if (windowSize % 2 != 0)
            throw new IllegalArgumentException("keyword-fft-window-size");
        int melLength = config
            .getInteger("keyword-mel-frame-length", DEFAULT_MEL_FRAME_LENGTH)
            * sampleRate / 1000 / this.hopLength;
        this.melWidth = config
            .getInteger("keyword-mel-frame-width", DEFAULT_MEL_FRAME_WIDTH);

        // allocate the stft window and FFT/frame buffer
        if (windowType.equals(FFT_WINDOW_TYPE_HANN))
            this.fftWindow = hannWindow(windowSize);
        else
            throw new IllegalArgumentException("keyword-fft-window-type");

        this.fft = new FloatFFT_1D(windowSize);
        this.fftFrame = new float[windowSize];

        // fetch and validate encoder configuration
        int encodeLength = config
            .getInteger("keyword-encode-length", DEFAULT_ENCODE_LENGTH)
            * sampleRate / 1000 / this.hopLength;
        this.encodeWidth = config
            .getInteger("keyword-encode-width", DEFAULT_ENCODE_WIDTH);
        int stateWidth = config
            .getInteger("keyword-state-width", this.encodeWidth);

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
            .setPath(config.getString("keyword-filter-path"))
            .load();
        loader.reset();
        this.encodeModel = loader
            .setPath(config.getString("keyword-encode-path"))
            .setStatePosition(1)
            .load();
        loader.reset();
        this.detectModel = loader
            .setPath(config.getString("keyword-detect-path"))
            .load();

        // configure the keyword probability threshold
        this.threshold = (float) config
            .getDouble("keyword-threshold", (double) DEFAULT_THRESHOLD);
    }

    /**
     * releases resources associated with the keyword recognizer.
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
        // audio samples are written to it
        this.sampleWindow.reset();

        // reset and fill the other buffers,
        // which prevents them from delaying detection
        // the encoder has a tanh nonlinearity, so fill it with -1
        this.frameWindow.reset().fill(0);
        this.encodeWindow.reset().fill(-1);

        // reset the encoder states
        while (this.encodeModel.states().hasRemaining())
            this.encodeModel.states().putFloat(0);
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param buffer  the audio frame to detect
     * @throws Exception on error
     */
    public void process(SpeechContext context, ByteBuffer buffer)
            throws Exception {
        // run the current frame through the detector pipeline
        sample(context, buffer);

        // on deactivation, see if a keyword was detected
        if (!context.isActive() && this.isActive)
            detect(context);

        this.isActive = context.isActive();
    }

    private void sample(SpeechContext context, ByteBuffer buffer) {
        // process all samples in the frame
        buffer.rewind();
        while (buffer.hasRemaining()) {
            // convert to float and clip the 16-bit sample
            float sample = (float) buffer.getShort() / Short.MAX_VALUE;
            sample = Math.max(-1f, Math.min(sample, 1f));

            // run a pre-emphasis filter to balance high frequencies
            // and eliminate any dc energy
            float nextSample = sample;
            sample -= this.preEmphasis * this.prevSample;
            this.prevSample = nextSample;

            // process the sample
            // . write it to the sample sliding window
            // . run the remainder of the detection pipeline if active
            // . advance the sliding window by the hop length
            this.sampleWindow.write(sample);
            if (this.sampleWindow.isFull()) {
                if (context.isActive())
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

        // copy the current mel frame into the frame window
        this.frameWindow.rewind().seek(this.melWidth);
        while (this.filterModel.outputs(0).hasRemaining())
            this.frameWindow.write(this.filterModel.outputs(0).getFloat());

        encode(context);
    }

    private void encode(SpeechContext context) {
        // transfer the mel filterbank window to the encoder model's inputs
        this.frameWindow.rewind();
        this.encodeModel.inputs(0).rewind();
        while (!this.frameWindow.isEmpty())
            this.encodeModel.inputs(0).putFloat(this.frameWindow.read());

        // run the encoder tensorflow model
        this.encodeModel.run();

        // copy the encoder output into the encode window
        this.encodeWindow.rewind().seek(this.encodeWidth);
        while (this.encodeModel.outputs(0).hasRemaining())
            this.encodeWindow.write(this.encodeModel.outputs(0).getFloat());
    }

    private void detect(SpeechContext context) {
        String transcript = null;
        float confidence = 0;

        // transfer the encoder window to the detector model's inputs
        this.encodeWindow.rewind();
        this.detectModel.inputs(0).rewind();
        while (!this.encodeWindow.isEmpty())
            this.detectModel.inputs(0).putFloat(this.encodeWindow.read());

        // run the classifier tensorflow model
        this.detectModel.run();

        // check the classifier's output and find the most likely class
        for (int i = 0; i < this.classes.length; i++) {
            float posterior = this.detectModel.outputs(0).getFloat();
            if (posterior > confidence) {
                transcript = this.classes[i];
                confidence = posterior;
            }
        }

        context.traceInfo("keyword: %.3f %s", confidence, transcript);

        if (confidence >= this.threshold) {
            // raise the speech recognition event with the class transcript
            context.setTranscript(transcript);
            context.setConfidence(confidence);
            context.dispatch(SpeechContext.Event.RECOGNIZE);
        } else {
            // if we are under threshold, time out without recognition
            context.dispatch(SpeechContext.Event.TIMEOUT);
        }

        reset();
    }

    private float[] hannWindow(int len) {
        // https://en.wikipedia.org/wiki/Hann_function
        float[] window = new float[len];
        for (int i = 0; i < len; i++)
            window[i] = (float) Math.pow(Math.sin(Math.PI * i / (len - 1)), 2);
        return window;
    }
}
