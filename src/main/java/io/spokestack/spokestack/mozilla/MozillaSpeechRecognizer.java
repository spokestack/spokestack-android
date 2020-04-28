package io.spokestack.spokestack.mozilla;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechProcessor;
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechModel;
import org.mozilla.deepspeech.libdeepspeech.DeepSpeechStreamingState;

import java.nio.ByteBuffer;

/**
 * Mozilla DeepSpeech recognizer
 *
 * <p>
 * This pipeline component uses Mozilla's on-device DeepSpeech API to perform
 * speech recognition. When the speech context is triggered, the recognizer
 * begins streaming buffered frames to the local model for recognition. Once the
 * speech context becomes inactive, the recognizer ends the stream and raises a
 * RECOGNIZE event along with the audio transcript and confidence.
 * </p>
 *
 * <p>
 * This pipeline component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>mozilla-model-path</b> (string): path to Mozilla DeepSpeech model
 *   </li>
 *   <li>
 *      <b>mozilla-scorer-path</b> (string): path to Mozilla ASR scorer
 *   </li>
 *   <li>
 *      <b>beam-width</b> (string): width of beam for ASR decoder
 *   </li>
 *   <li>
 *      <b>locale</b> (string): language code for speech recognition
 *   </li>
 * </ul>
 */
public final class MozillaSpeechRecognizer implements SpeechProcessor {
    private final DeepSpeechModel model;

    private DeepSpeechStreamingState modelState;
    private int frameSize;

    /**
     * initializes a new recognizer instance.
     *
     * @param speechConfig spokestack pipeline configuration
     * @throws Exception on error
     */
    public MozillaSpeechRecognizer(SpeechConfig speechConfig) throws Exception {
        String modelPath = speechConfig.getString("mozilla-model-path");
        this.model = new DeepSpeechModel(modelPath);
        configure(speechConfig);
    }

    /**
     * initializes a new recognizer instance with an existing model, for
     * testing/mocking purposes.
     *
     * @param speechConfig spokestack pipeline configuration
     * @param asrModel     preloaded recognition model
     * @throws Exception on error
     */
    public MozillaSpeechRecognizer(
          SpeechConfig speechConfig,
          DeepSpeechModel asrModel) throws Exception {
        this.model = asrModel;
        configure(speechConfig);
    }

    private void configure(SpeechConfig speechConfig) {
        int sampleRate = speechConfig.getInteger("sample-rate");
        int frameWidth = speechConfig.getInteger("frame-width");
        this.frameSize = sampleRate * frameWidth / 1000;
        int beamWidth = speechConfig.getInteger("beam-width", 50);
        this.model.setBeamWidth(beamWidth);
        if (speechConfig.containsKey("mozilla-scorer-path")) {
            String scorerPath = speechConfig.getString("mozilla-scorer-path");
            this.model.enableExternalScorer(scorerPath);
        }

    }

    /**
     * releases the resources associated with the recognizer.
     */
    public void close() {
        this.modelState = null;
        this.model.freeModel();
    }

    /**
     * processes a frame of audio.
     *
     * @param context the current speech context
     * @param frame   the audio frame to detect
     * @throws Exception on error
     */
    public void process(SpeechContext context, ByteBuffer frame)
          throws Exception {
        if (context.isActive() && this.modelState == null) {
            begin(context);
        } else if (!context.isActive() && this.modelState != null) {
            commit(context);
        } else if (context.isActive()) {
            send(frame);
        }
    }

    private void begin(SpeechContext context) {
        this.modelState = model.createStream();

        for (ByteBuffer frame : context.getBuffer()) {
            send(frame);
        }
    }

    private void send(ByteBuffer frame) {
        short[] shortBuf = convertFrame(frame);
        this.model.feedAudioContent(
              this.modelState, shortBuf, shortBuf.length);
    }

    private short[] convertFrame(ByteBuffer frame) {
        frame.rewind();
        short[] shortBuf = new short[this.frameSize];
        frame.asShortBuffer().get(shortBuf);
        return shortBuf;
    }

    private void commit(SpeechContext context) {
        String decoded = this.model.finishStream(this.modelState);
        dispatchResult(decoded, context);
        this.modelState = null;
    }

    private void dispatchResult(String result, SpeechContext context) {
        context.setTranscript(result);
        context.setConfidence(1.0);
        context.dispatch(SpeechContext.Event.RECOGNIZE);
    }
}
