package io.spokestack.spokestack.google;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import com.google.protobuf.ByteString;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;

import io.spokestack.spokestack.ComponentConfig;
import io.spokestack.spokestack.SpeechProcessor;
import io.spokestack.spokestack.SpeechContext;

/**
 * google speech api recognizer
 *
 * <p>
 * This pipeline component uses the Google Speech API to stream audio samples
 * from spokestack for speech recognition. When the speech context is
 * triggered, the recognizer begins streaming buffered frames to the API
 * for recognition. Once the speech context becomes inactive, the recognizer
 * completes the API request and raises a RECOGNIZE event along with the audio
 * transcript and confidence.
 * </p>
 *
 * <p>
 * This pipeline component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>sample-rate</b> (integer): audio sampling rate, in Hz
 *   </li>
 *   <li>
 *      <b>google-credentials</b> (string): json-stringified google service
 *      account credentials, used to authenticate with the speech API
 *   </li>
 *   <li>
 *      <b>locale</b> (string): language code for speech recognition
 *   </li>
 * </ul>
 */
public final class GoogleSpeechRecognizer implements SpeechProcessor {
    private final SpeechClient client;
    private StreamingRecognitionConfig config;
    private ApiStreamObserver<StreamingRecognizeRequest> request;

    /**
     * initializes a new recognizer instance.
     * @param componentConfig spokestack pipeline configuration
     * @throws Exception on error
     */
    public GoogleSpeechRecognizer(ComponentConfig componentConfig) throws Exception {
        String credentials = componentConfig.getString("google-credentials");
        FixedCredentialsProvider clientCredentials =
            FixedCredentialsProvider.create(
                ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(credentials.getBytes("utf-8"))));

        this.client = SpeechClient.create(SpeechSettings.newBuilder()
            .setCredentialsProvider(clientCredentials)
            .build()
        );

        configure(componentConfig);
    }

    /**
     * initializes a new recognizer instance with an existing google client,
     * for testing/mocking purposes.
     * @param componentConfig spokestack pipeline configuration
     * @param speechClient google speech api client
     * @throws Exception on error
     */
    public GoogleSpeechRecognizer(
            ComponentConfig componentConfig,
            SpeechClient speechClient) throws Exception {
        this.client = speechClient;
        configure(componentConfig);
    }

    private void configure(ComponentConfig componentConfig) throws Exception {
        int sampleRate = componentConfig.getInteger("sample-rate");
        String locale = componentConfig.getString("locale");

        RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setLanguageCode(locale)
            .setSampleRateHertz(sampleRate)
            .build();
        this.config = StreamingRecognitionConfig.newBuilder()
            .setConfig(recognitionConfig)
            .setInterimResults(false)
            .build();
    }

    /**
     * releases the resources associated with the recognizer.
     * @throws Exception on error
     */
    public void close() throws Exception {
        if (this.request != null)
            this.request.onCompleted();
        this.client.close();
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param frame   the audio frame to detect
     * @throws Exception on error
     */
    public void process(SpeechContext context, ByteBuffer frame)
            throws Exception {
        if (context.isActive() && this.request == null)
            begin(context);
        else if (!context.isActive() && this.request != null)
            commit();
        else if (context.isActive())
            send(frame);
    }

    private void begin(SpeechContext context) {
        BidiStreamingCallable<
            StreamingRecognizeRequest,
            StreamingRecognizeResponse> callable =
            this.client.streamingRecognizeCallable();
        this.request = callable.bidiStreamingCall(
            new ResponseObserver(context));

        // send the configuration payload first
        this.request.onNext(
            StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(this.config)
                .build()
        );

        // send any buffered frames to the api
        // based on integration testing,
        // these are transmitted asynchronously by the speech client
        // so they don't appear to block the frame loop
        for (ByteBuffer frame: context.getBuffer())
            send(frame);
    }

    private void send(ByteBuffer frame) {
        frame.rewind();
        this.request.onNext(
            StreamingRecognizeRequest.newBuilder()
                .setAudioContent(ByteString.copyFrom(frame))
                .build()
        );
    }

    private void commit() {
        try {
            this.request.onCompleted();
        } finally {
            // be sure to detach the request,
            // so we can ride over transient errors
            this.request = null;
        }
    }

    /**
     * speech api asynchronous response callback.
     */
    private static class ResponseObserver
            implements ApiStreamObserver<StreamingRecognizeResponse> {
        private final SpeechContext context;
        private String transcript = "";
        private double confidence;

        ResponseObserver(SpeechContext speechContext) {
            this.context = speechContext;
        }

        @Override
        public void onNext(StreamingRecognizeResponse message) {
            // we aren't using intermediate recognition results,
            // so just attach the first result
            StreamingRecognitionResult result = message.getResults(0);
            SpeechRecognitionAlternative alt = result.getAlternatives(0);
            this.transcript = alt.getTranscript();
            this.confidence = alt.getConfidence();
        }

        @Override
        public void onError(Throwable e) {
            this.context.setError(e);
            this.context.dispatch(SpeechContext.Event.ERROR);
        }

        @Override
        public void onCompleted() {
            this.context.setTranscript(this.transcript);
            this.context.setConfidence(this.confidence);
            if (this.transcript != "")
                this.context.dispatch(SpeechContext.Event.RECOGNIZE);
            else
                this.context.dispatch(SpeechContext.Event.TIMEOUT);
        }
    }
}
