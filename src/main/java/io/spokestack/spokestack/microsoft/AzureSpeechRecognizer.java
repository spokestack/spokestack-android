package io.spokestack.spokestack.microsoft;

import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ProfanityOption;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionCanceledEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionEventArgs;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import com.microsoft.cognitiveservices.speech.util.EventHandler;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * microsoft azure speech service recognizer
 *
 * <p>
 * This component implements the speech processor interface using the Azure
 * Speech Service for speech recognition.
 * </p>
 *
 * <p>
 * When the speech context is triggered, the recognizer begins streaming
 * buffered frames to the API for recognition. Once the speech context becomes
 * inactive, the recognizer raises a RECOGNIZE event along with the audio
 * transcript. Unfortunately, the Azure Speech SDK currently doesn't return
 * confidence values alongside transcripts, so confidence is always set to 1.0.
 * </p>
 *
 * <p>
 * Use of the Azure Speech Service implies acceptance of Microsoft's license
 * terms, which can be found
 * <a href=
 * "https://csspeechstorage.blob.core.windows.net/drop/license201809.html">
 * here</a>.
 * </p>
 *
 * <p>
 * This pipeline component requires the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>sample-rate</b> (integer): audio sampling rate, in Hz
 *   </li>
 *   <li>
 *      <b>frame-width</b> (integer): speech frame width, in ms
 *   </li>
 *   <li>
 *      <b>locale</b> (string): language code for speech recognition
 *   </li>
 *   <li>
 *      <b>azure-api-key</b> (string): API key for the Azure Speech
 *      service
 *   </li>
 *   <li>
 *      <b>azure-region</b> (string): Azure Speech service region
 *   </li>
 * </ul>
 */
public class AzureSpeechRecognizer implements SpeechProcessor {
    private final com.microsoft.cognitiveservices.speech.SpeechConfig msConfig;

    private SpeechRecognizer recognizer;
    private PushAudioInputStream audioStream;
    private AudioConfig audioConfig;
    private boolean active;

    // Azure speech requires little-endian (wav-format) data, so we buffer
    // audio frames internally to avoid mutating data coming from the speech
    // context
    private final ByteBuffer buffer;

    /**
     * initializes a new recognizer instance.
     *
     * @param speechConfig Spokestack speech configuration
     */
    public AzureSpeechRecognizer(SpeechConfig speechConfig) {
        String apiKey = speechConfig.getString("azure-api-key");
        String region = speechConfig.getString("azure-region");
        int sampleRate = speechConfig.getInteger("sample-rate");

        if (sampleRate != 16000) {
            throw new IllegalArgumentException(
                  "Azure only supports a 16kHz sample rate; found: "
                        + sampleRate);
        }

        this.buffer = ByteBuffer.allocateDirect(4096)
              .order(ByteOrder.LITTLE_ENDIAN);
        this.msConfig = createMsConfig(apiKey, region);
    }

    com.microsoft.cognitiveservices.speech.SpeechConfig createMsConfig(
          String apiKey, String region) {
        com.microsoft.cognitiveservices.speech.SpeechConfig config =
              com.microsoft.cognitiveservices.speech.SpeechConfig
                    .fromSubscription(apiKey, region);
        config.setProfanity(ProfanityOption.Raw);
        return config;
    }

    /**
     * releases the resources associated with the recognizer.
     */
    public void close() {
        if (this.audioStream != null) {
            this.audioStream.close();
            this.audioStream = null;
        }
        if (this.recognizer != null) {
            this.recognizer.close();
            this.recognizer = null;
        }
    }

    /**
     * processes a frame of audio.
     *
     * @param speechContext the current speech context
     * @param frame         the audio frame to detect
     *
     * @throws Exception if there is an error performing active recognition.
     */
    public void process(SpeechContext speechContext, ByteBuffer frame)
          throws Exception {
        if (speechContext.isActive() && !this.active) {
            begin(speechContext);
        } else if (!speechContext.isActive() && this.active) {
            commit();
        } else if (speechContext.isActive()) {
            bufferFrame(frame);
        }
    }

    void begin(SpeechContext speechContext) {
        this.audioStream = AudioInputStream.createPushStream();
        this.audioConfig = AudioConfig.fromStreamInput(this.audioStream);
        this.recognizer = createRecognizer(speechContext);
        recognizer.startContinuousRecognitionAsync();
        this.active = true;

        // send any existing frames into the stream
        for (ByteBuffer frame : speechContext.getBuffer()) {
            bufferFrame(frame);
        }
    }

    SpeechRecognizer createRecognizer(SpeechContext context) {
        // factored into a separate method for testing
        SpeechRecognizer rec = new SpeechRecognizer(msConfig, audioConfig);
        listen(rec, context);
        return rec;
    }

    private void listen(SpeechRecognizer rec, SpeechContext context) {
        RecognitionListener recognitionListener =
              new RecognitionListener(context);
        rec.recognized.addEventListener(recognitionListener);

        CancellationListener cancellationListener =
              new CancellationListener(context);
        rec.canceled.addEventListener(cancellationListener);
    }

    void bufferFrame(ByteBuffer frame) {
        if (frame != null) {
            if (this.buffer.remaining() < frame.capacity()) {
                flush();
            }

            frame.rewind();
            this.buffer.put(frame);
        }
    }

    void commit() throws Exception {
        // send the end of audio
        flush();
        this.audioStream.close();
        this.recognizer.stopContinuousRecognitionAsync().get();
        this.recognizer.close();
        this.audioConfig.close();
        this.active = false;
    }

    private void flush() {
        if (this.buffer.hasArray()) {
            this.buffer.flip();
            this.audioStream.write(this.buffer.array());
            this.buffer.clear();
        }
    }

    /**
     * Listener for Speech SDK recognition events.
     */
    static class RecognitionListener
          implements EventHandler<SpeechRecognitionEventArgs> {
        private final SpeechContext speechContext;

        RecognitionListener(SpeechContext context) {
            this.speechContext = context;
        }

        @Override
        public void onEvent(
              Object sender,
              SpeechRecognitionEventArgs recognitionArgs) {
            ResultReason reason = recognitionArgs.getResult().getReason();
            String transcript = recognitionArgs.getResult().getText();
            if (reason == ResultReason.RecognizingSpeech) {
                dispatchResult(transcript,
                      SpeechContext.Event.PARTIAL_RECOGNIZE);
            } else if (reason == ResultReason.RecognizedSpeech) {
                dispatchResult(transcript, SpeechContext.Event.RECOGNIZE);
            }
        }

        private void dispatchResult(String transcript,
                                    SpeechContext.Event event) {
            this.speechContext.setTranscript(transcript);
            this.speechContext.setConfidence(1.0);
            this.speechContext.dispatch(event);
        }
    }

    /**
     * Listener for Speech SDK cancellation events.
     */
    static class CancellationListener
          implements EventHandler<SpeechRecognitionCanceledEventArgs> {

        private final SpeechContext speechContext;

        CancellationListener(SpeechContext context) {
            this.speechContext = context;
        }

        @Override
        public void onEvent(
              Object sender,
              SpeechRecognitionCanceledEventArgs cancellationArgs) {
            if (cancellationArgs.getReason()
                  == CancellationReason.Error) {

                String message = String.format(
                      "%s (error code %s)",
                      cancellationArgs.getErrorDetails(),
                      cancellationArgs.getErrorCode().name());

                this.speechContext.setError(new Exception(message));
                this.speechContext.dispatch(SpeechContext.Event.ERROR);
            }
        }
    }
}
