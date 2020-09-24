package io.spokestack.spokestack.asr;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import io.spokestack.spokestack.SpeechProcessor;

import java.nio.ByteBuffer;

/**
 * Spokestack cloud speech recognizer
 *
 * <p>
 * This pipeline component streams audio samples to Spokestack's cloud-based ASR
 * service for speech recognition. When the speech context is triggered, the
 * recognizer begins streaming buffered frames to the API for recognition. Once
 * the speech context becomes inactive, the recognizer completes the API request
 * and raises a RECOGNIZE event along with the audio transcript and confidence.
 * </p>
 *
 * <p>
 * The Spokestack recognizer supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *       <b>spokestack-id</b> (string, required): The client ID used for
 *       synthesis requests.
 *   </li>
 *   <li>
 *       <b>spokestack-secret</b> (string, required): The client secret used
 *       to sign synthesis requests.
 *   </li>
 *   <li>
 *      <b>sample-rate</b> (integer): audio sampling rate, in Hz
 *   </li>
 * </ul>
 */
public final class SpokestackCloudRecognizer implements SpeechProcessor {
    private static final int IDLE_TIMEOUT = 5000;
    private static final String LANGUAGE = "en";

    private final SpokestackCloudClient client;
    private final int maxIdleCount;
    private SpeechContext context;
    private int idleCount;
    private boolean active;

    /**
     * initializes a new recognizer instance.
     *
     * @param speechConfig Spokestack speech configuration
     */
    public SpokestackCloudRecognizer(SpeechConfig speechConfig) {
        this(speechConfig, new SpokestackCloudClient.Builder());
    }

    /**
     * initializes a new recognizer instance, useful for testing.
     *
     * @param speechConfig Spokestack speech configuration
     * @param builder      speech client builder
     */
    public SpokestackCloudRecognizer(
          SpeechConfig speechConfig,
          SpokestackCloudClient.Builder builder) {
        String clientId = speechConfig.getString("spokestack-id");
        String secret = speechConfig.getString("spokestack-secret");
        String language = speechConfig.getString("language", LANGUAGE);
        int sampleRate = speechConfig.getInteger("sample-rate");
        int frameWidth = speechConfig.getInteger("frame-width");

        this.maxIdleCount = IDLE_TIMEOUT / frameWidth;

        this.client = builder
              .setCredentials(clientId, secret)
              .setLang(language)
              .setSampleRate(sampleRate)
              .setListener(new Listener())
              .build();
    }

    @Override
    public void reset() {
        if (this.client.isConnected()) {
            this.client.disconnect();
        }
        this.idleCount = 0;
        this.active = false;
    }

    /**
     * releases the resources associated with the recognizer.
     */
    @Override
    public void close() {
        this.client.close();
    }

    /**
     * processes a frame of audio.
     *
     * @param speechContext the current speech context
     * @param frame         the audio frame to detect
     */
    public void process(SpeechContext speechContext, ByteBuffer frame) {
        this.context = speechContext;
        if (speechContext.isActive() && !this.active) {
            begin();
        } else if (!speechContext.isActive() && this.active) {
            commit();
        } else if (speechContext.isActive()) {
            send(frame);
        } else if (++this.idleCount > this.maxIdleCount) {
            this.client.disconnect();
        }
    }

    private void begin() {
        if (!this.client.isConnected()) {
            this.client.connect();
        }
        this.client.init();
        this.active = true;
        this.idleCount = 0;

        for (ByteBuffer frame : context.getBuffer()) {
            send(frame);
        }
    }

    private void send(ByteBuffer frame) {
        this.client.sendAudio(frame);
    }

    private void commit() {
        // send the end of audio
        this.active = false;
        this.client.endAudio();
    }

    /**
     * speech recognizer listener.
     */
    private class Listener implements SpokestackCloudClient.Listener {
        public void onSpeech(String transcript,
                             float confidence,
                             boolean isFinal) {
            if (!transcript.equals("")) {
                context.setTranscript(transcript);
                context.setConfidence(confidence);
                SpeechContext.Event event = (isFinal)
                      ? SpeechContext.Event.RECOGNIZE
                      : SpeechContext.Event.PARTIAL_RECOGNIZE;
                context.dispatch(event);
            }
        }

        public void onError(Throwable e) {
            client.disconnect();
            active = false;

            context.setError(e);
            context.dispatch(SpeechContext.Event.ERROR);
        }
    }
}
