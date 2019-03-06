package com.pylon.spokestack.wakeword;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.jtransforms.fft.FloatFFT_1D;

import com.pylon.spokestack.SpeechConfig;
import com.pylon.spokestack.SpeechProcessor;
import com.pylon.spokestack.SpeechContext;
import com.pylon.spokestack.tensorflow.TensorflowModel;

/**
 * wakeword Detection pipeline component
 *
 * <p>
 * WakewordTrigger is a speech pipeline component that provides wakeword
 * detection for activating downstream components. It uses a Tensorflow-Lite
 * classifier to detect keywords (i.e. [null], up, dog) and aggregates them
 * into phrases (up dog). Once a wakeword phrase is detected, the pipeline
 * is activated. The pipeline remains active until the user stops talking
 * or the activation timeout is reached.
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
 * The mel spectrogram represents the features to be passed to the keyword
 * classifier, which is implemented in a "detect" Tensorflow model. This
 * classifier outputs posterior probabilities for each keyword (and a null
 * output 0, which represents non-keyword speech).
 * </p>
 *
 * <p>
 * The detector's outputs are considered noisy, so they are maintained in a
 * sliding window and passed through a moving mean filter. The smoothed
 * posteriors are then maintained in another sliding window for phrasing. The
 * phraser attempts to match one of the configured keyword sequences using
 * the maximum posterior for each word. If a sequence match is found, the
 * speech pipeline is activated.
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
 *      <b>wake-words</b> (string, required): a comma-separated list of the
 *      keywords, in the order they appear in the classifier outputs, not
 *      including the null (non-keyword) class
 *      (ex: "up,dog")
 *   </li>
 *   <li>
 *      <b>wake-phrases</b> (string): a comma-separated list of
 *      space-separated keyword phrases to detect, which defaults to no
 *      phrases (just individual keywords)
 *      (ex: "up dog,dog dog")
 *   </li>
 *   <li>
 *      <b>wake-filter-path</b> (string, required): file system path to the
 *      "filter" Tensorflow-Lite model, which is used to calculate a mel
 *      spectrogram frame from the linear STFT; its inputs should be shaped
 *      [fft-width], and its outputs [mel-width]
 *   </li>
 *   <li>
 *      <b>wake-detect-path</b> (string, required): file system path to the
 *      "detect" Tensorflow-Lite model; its inputs shoudld be shaped
 *      [mel-length, mel-width], and its outputs [word-count + 1]
 *   </li>
 *   <li>
 *      <b>wake-smooth-length</b> (integer): the length of the posterior
 *      smoothing window to use with the classifier's outputs, in milliseconds
 *   </li>
 *   <li>
 *      <b>wake-phrase-length</b> (integer): the length of the phraser's
 *      sliding window, in milliseconds - this value should be long enough to
 *      fit the longest supported phrase
 *   </li>
 *   <li>
 *      <b>wake-active-min</b> (integer): the minimum length of an activation,
 *      in milliseconds, used to ignore a VAD deactivation after the wakeword
 *   </li>
 *   <li>
 *      <b>wake-active-max</b> (integer): the maximum length of an activation,
 *      in milliseconds, used to time out the activation
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
 *      used as an input to the classifier, in milliseconds
 *   </li>
 *   <li>
 *      <b>mel-frame-width</b> (integer): the size of each mel spectrogram
 *      frame, in number of filterbank components
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
    public static final int DEFAULT_MEL_FRAME_LENGTH = 400;
    /** default mel-frame-width configuration value. */
    public static final int DEFAULT_MEL_FRAME_WIDTH = 40;
    /** default wake-smooth-length configuration value. */
    public static final int DEFAULT_WAKE_SMOOTH_LENGTH = 300;
    /** default wake-frame-length configuration value. */
    public static final int DEFAULT_WAKE_PHRASE_LENGTH = 500;
    /** default wake-active-min configuration value. */
    public static final int DEFAULT_WAKE_ACTIVE_MIN = 500;
    /** default wake-active-max configuration value. */
    public static final int DEFAULT_WAKE_ACTIVE_MAX = 5000;

    // voice activity detection
    private boolean isSpeech;

    // keyword/phrase configuration and preallocated buffers
    private final String[] words;
    private final int[][] phrases;
    private final float[] phraseSum;
    private final int[] phraseArg;
    private final float[] phraseMax;

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

    // sliding window buffers
    private final RingBuffer sampleWindow;
    private final RingBuffer frameWindow;
    private final RingBuffer smoothWindow;
    private final RingBuffer phraseWindow;

    // tensorflow mel filtering and classifier models
    private final TensorflowModel filterModel;
    private final TensorflowModel detectModel;

    // wakeword activation management
    private final int minActive;
    private final int maxActive;
    private int activeLength;

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
        // parse the configured list of keywords
        // allocate an additional slot for the non-keyword class at 0
        List<String> wakeWords = Arrays
            .asList(config.getString("wake-words").split(","));
        this.words = new String[wakeWords.size() + 1];
        for (int i = 1; i < this.words.length; i++)
            this.words[i] = wakeWords.get(i - 1);

        // parse the keyword phrase configuration
        String[] wakePhrases = config
            .getString("wake-phrases", String.join(",", wakeWords))
            .split(",");
        this.phrases = new int[wakePhrases.length][];
        for (int i = 0; i < wakePhrases.length; i++) {
            String[] wakePhrase = wakePhrases[i].split(" ");

            // allocate an additional (null) slot at the end of each phrase,
            // which forces the phraser to continue detection until the end
            // of the final keyword in each phrase
            this.phrases[i] = new int[wakePhrase.length + 1];
            for (int j = 0; j < wakePhrase.length; j++) {
                // verify that each keyword in the phrase is a known keyword
                int k = wakeWords.indexOf(wakePhrase[j]);
                if (k == -1)
                    throw new IllegalArgumentException("wake-phrases");

                this.phrases[i][j] = k + 1;
            }
        }

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

        // fetch smoothing/phrasing window lengths
        int smoothLength = config
            .getInteger("wake-smooth-length", DEFAULT_WAKE_SMOOTH_LENGTH)
            * sampleRate / 1000 / this.hopLength;
        int phraseLength = config
            .getInteger("wake-phrase-length", DEFAULT_WAKE_PHRASE_LENGTH)
            * sampleRate / 1000 / this.hopLength;

        // allocate sliding windows
        // fill all buffers (except samples) with zero, in order to
        // minimize detection delay caused by buffering
        this.sampleWindow = new RingBuffer(windowSize);
        this.frameWindow = new RingBuffer(melLength * this.melWidth);
        this.smoothWindow = new RingBuffer(smoothLength * this.words.length);
        this.phraseWindow = new RingBuffer(phraseLength * this.words.length);

        this.frameWindow.fill(0);
        this.smoothWindow.fill(0);
        this.phraseWindow.fill(0);

        // load the tensorflow-lite models
        this.filterModel = loader
            .setPath(config.getString("wake-filter-path"))
            .setInputShape(windowSize / 2 + 1)
            .setOutputShape(this.melWidth)
            .load();
        this.detectModel = loader
            .setPath(config.getString("wake-detect-path"))
            .setInputShape(melLength * this.melWidth)
            .setOutputShape(this.words.length)
            .load();

        // preallocate the buffers used for posterior smoothing
        // and argmax used for phrasing, so that we don't do
        // any allocation within the frame loop
        this.phraseSum = new float[this.words.length];
        this.phraseMax = new float[this.words.length];
        this.phraseArg = new int[phraseLength];

        // configure the wakeword activation lengths
        int frameWidth = config.getInteger("frame-width");
        this.minActive = config
            .getInteger("wake-active-min", DEFAULT_WAKE_ACTIVE_MIN)
            / frameWidth;
        this.maxActive = config
            .getInteger("wake-active-max", DEFAULT_WAKE_ACTIVE_MAX)
            / frameWidth;
    }

    /**
     * releases resources associated with the wakeword detector.
     * @throws Exception on error
     */
    public void close() throws Exception {
        this.filterModel.close();
        this.detectModel.close();
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
        boolean vadRise = !this.isSpeech && context.isSpeech();
        boolean vadFall = this.isSpeech && !context.isSpeech();
        this.isSpeech = context.isSpeech();

        if (!context.isActive()) {
            // run the current frame through the detector pipeline
            // activate if a keyword phrase was detected
            sample(context, buffer);
        } else {
            // continue this wakeword (or external) activation
            // until a vad deactivation or timeout
            if (++this.activeLength > this.minActive) {
                if (vadFall) {
                    deactivate(context);
                } else if (this.activeLength > this.maxActive) {
                    timedOut(context);
                    deactivate(context);
                }
            }
        }

        // always clear detector state on a vad deactivation
        // this prevents <keyword1><pause><keyword2> detection
        if (vadFall)
            reset(context);
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
        this.filterModel.inputs().rewind();
        this.filterModel.inputs().putFloat(this.fftFrame[0]);
        for (int i = 1; i < this.fftFrame.length / 2; i++) {
            float re = this.fftFrame[i * 2 + 0];
            float im = this.fftFrame[i * 2 + 1];
            float ab = (float) Math.sqrt(re * re + im * im);
            this.filterModel.inputs().putFloat(ab);
        }
        this.filterModel.inputs().putFloat(this.fftFrame[1]);

        // execute the mel filterbank tensorflow model
        this.filterModel.run();

        // copy the current mel frame into the mel window
        this.frameWindow.rewind().seek(this.melWidth);
        while (this.filterModel.outputs().hasRemaining())
            this.frameWindow.write(this.filterModel.outputs().getFloat());

        detect(context);
    }

    private void detect(SpeechContext context) {
        // transfer the mel filterbank window to the detector model's inputs
        this.frameWindow.rewind();
        this.detectModel.inputs().rewind();
        while (!this.frameWindow.isEmpty())
            this.detectModel.inputs().putFloat(this.frameWindow.read());

        // run the classifier tensorflow model
        this.detectModel.run();

        // transfer the classifier's outputs to the posterior smoothing window
        this.smoothWindow.rewind().seek(this.words.length);
        while (this.detectModel.outputs().hasRemaining())
            this.smoothWindow.write(this.detectModel.outputs().getFloat());

        smooth(context);
    }

    private void smooth(SpeechContext context) {
        // sum the per-class posteriors across the smoothing window
        for (int i = 0; i < this.words.length; i++)
            this.phraseSum[i] = 0;
        while (!this.smoothWindow.isEmpty())
            for (int i = 0; i < this.words.length; i++)
                this.phraseSum[i] += this.smoothWindow.read();

        // compute the posterior mean of each keyword class
        // write the outputs to the phrasing window
        int total = this.smoothWindow.capacity() / this.words.length;
        this.phraseWindow.rewind().seek(this.words.length);
        for (int i = 0; i < this.words.length; i++)
            this.phraseWindow.write(this.phraseSum[i] / total);

        phrase(context);
    }

    private void phrase(SpeechContext context) {
        // compute the argmax (winning class) of each smoothed output
        // in the current phrase window
        for (int i = 0; !this.phraseWindow.isEmpty(); i++) {
            float max = -Float.MAX_VALUE;
            for (int j = 0; j < this.words.length; j++) {
                float value = this.phraseWindow.read();
                this.phraseMax[j] = Math.max(value, this.phraseMax[j]);
                if (value > max) {
                    this.phraseArg[i] = j;
                    max = value;
                }
            }
        }

        // attempt to find a matching phrase among the argmaxes
        for (int[] phrase : this.phrases) {
            // search for any occurrences of the phrase's keywords in order
            // across the whole phrase window
            int match = 0;
            for (int word: this.phraseArg)
                if (word == phrase[match])
                    if (++match == phrase.length)
                        break;

            // if we reached the end of a phrase, we have a match,
            // so start the activation counter
            if (match == phrase.length) {
                activate(context);
                break;
            }
        }
    }

    private void activate(SpeechContext context) {
        if (!context.isActive()) {
            trace(context);
            this.activeLength = 1;
            context.setActive(true);
            context.dispatch(SpeechContext.Event.ACTIVATE);
        }
    }

    private void deactivate(SpeechContext context) {
        if (context.isActive()) {
            context.setActive(false);
            context.dispatch(SpeechContext.Event.DEACTIVATE);
            this.activeLength = 0;
        }
    }

    private void timedOut(SpeechContext context) {
        if (context.isActive()) {
            context.dispatch(SpeechContext.Event.TIMEDOUT);
        }
    }

    private void reset(SpeechContext context) {
        // trace on vad deactivate without detection
        if (!context.isActive())
            trace(context);

        // empty the sample buffer, so that only contiguous
        // speech samples are written to it
        this.sampleWindow.reset();

        // reset and fill the other buffers,
        // which prevents them from lagging the detection
        this.frameWindow.reset().fill(0);
        this.smoothWindow.reset().fill(0);
        this.phraseWindow.reset().fill(0);
        Arrays.fill(this.phraseMax, 0);
    }

    private void trace(SpeechContext context) {
        if (context.canTrace(SpeechContext.TraceLevel.INFO)) {
            StringBuilder message = new StringBuilder();
            message.append("wake: ");
            for (int i = 0; i < this.words.length; i++)
                message.append(
                    String.format(
                        "%s=%.2f ",
                        this.words[i],
                        this.phraseMax[i]));
            context.traceInfo(message.toString());
        }
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
