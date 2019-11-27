package io.spokestack.spokestack.microsoft;

import java.nio.ByteBuffer;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.SpeechContext;

/**
 * microsoft bing speech recognizer
 *
 * <p>
 * This component implements the speech processor interface using the
 * Bing Speech API for speech recognition. It uses the websocket protocol
 * defined <a href="https://goo.gl/qftX5j">here</a> to stream audio samples
 * and receive recognition results asynchronously.
 * </p>
 *
 * <p>
 * When the speech context is triggered, the recognizer begins streaming
 * buffered frames to the API for recognition. Once the speech context
 * becomes inactive, the recognizer raises a RECOGNIZE event along with the
 * audio transcript. Unfortunately, the Bing Speech API currently doesn't
 * return confidence values alongside transcripts, so confidence is always
 * set to 1.0.
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
 *      <b>bing-speech-api-key</b> (string): API key for the Bing Speech
 *      service
 *   </li>
 * </ul>
 */
public final class BingSpeechRecognizer implements SpeechProcessor {
    private static final int IDLE_TIMEOUT = 5000;

    private final BingSpeechClient client;
    private final int maxIdleCount;
    private SpeechContext context;
    private int idleCount;
    private boolean active;

    /**
     * initializes a new recognizer instance.
     * @param speechConfig Spokestack speech configuration
     * @throws Exception on error
     */
    public BingSpeechRecognizer(SpeechConfig speechConfig) throws Exception {
        this(speechConfig, new BingSpeechClient.Builder());
    }

    /**
     * initializes a new recognizer instance, useful for testing.
     * @param speechConfig Spokestack speech configuration
     * @param builder      speech client builder
     * @throws Exception on error
     */
    public BingSpeechRecognizer(
            SpeechConfig speechConfig,
            BingSpeechClient.Builder builder) throws Exception {
        String apiKey = speechConfig.getString("bing-speech-api-key");
        String locale = speechConfig.getString("locale");
        int sampleRate = speechConfig.getInteger("sample-rate");
        int frameWidth = speechConfig.getInteger("frame-width");

        this.maxIdleCount = IDLE_TIMEOUT / frameWidth;

        this.client = builder
            .setApiKey(apiKey)
            .setLocale(locale)
            .setSampleRate(sampleRate)
            .setListener(new Listener())
            .build();
    }

    /**
     * releases the resources associated with the recognizer.
     */
    public void close() {
        this.client.close();
    }

    /**
     * processes a frame of audio.
     * @param speechContext the current speech context
     * @param frame         the audio frame to detect
     * @throws Exception on error
     */
    public void process(SpeechContext speechContext, ByteBuffer frame)
            throws Exception {
        this.context = speechContext;
        if (speechContext.isActive() && !this.active)
            begin(speechContext);
        else if (!speechContext.isActive() && this.active)
            commit();
        else if (speechContext.isActive())
            send(frame);
        else if (++this.idleCount > this.maxIdleCount)
            this.client.disconnect();
    }

    private void begin(SpeechContext speechContext) throws Exception {
        // send the audio header
        if (!this.client.isConnected())
            this.client.connect();
        this.client.beginAudio();
        this.active = true;
        this.idleCount = 0;

        // send any buffered frames to the api
        for (ByteBuffer frame: context.getBuffer())
            send(frame);
    }

    private void send(ByteBuffer frame) throws Exception {
        this.client.sendAudio(frame);
    }

    private void commit() throws Exception {
        // send the end of audio
        this.active = false;
        this.client.endAudio();
    }

    /**
     * speech recognizer listener.
     */
    private class Listener implements BingSpeechClient.Listener {
        public void onSpeech(String transcript) {
            context.setTranscript(transcript);
            context.setConfidence(1.0);
            context.dispatch(SpeechContext.Event.RECOGNIZE);
        }

        public void onError(Throwable e) {
            client.disconnect();
            active = false;

            context.setError(e);
            context.dispatch(SpeechContext.Event.ERROR);
        }
    }
}
