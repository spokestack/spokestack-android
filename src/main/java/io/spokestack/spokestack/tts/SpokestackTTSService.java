package io.spokestack.spokestack.tts;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.SpeechConfig;
import okhttp3.Call;

import java.io.IOException;

/**
 * <p>
 * A TTS service component that uses the {@link SpokestackTTSClient} to
 * synthesize text.
 * </p>
 *
 * <p>
 * This component supports the following properties:
 * </p>
 * <ul>
 *     <li>
 *         <b>spokestack-id</b> (string, required): The client ID used for
 *         synthesis requests.
 *     </li>
 *     <li>
 *         <b>spokestack-secret</b> (string, required): The client secret used
 *         to sign synthesis requests.
 *     </li>
 *     <li>
 *         <b>spokestack-url</b> (string): The URL used for synthesis
 *         requests. Defaults to the synthesis URL active at the time this
 *         version of the library was published.
 *     </li>
 * </ul>
 */
public final class SpokestackTTSService extends TTSService {
    private SpokestackTTSClient client;

    /**
     * The callback used to process asynchronous responses from the underlying
     * HTTP client.
     */
    protected final TTSCallback callback = new SpokestackCallback();

    /**
     * Creates a new TTS service component.
     *
     * @param config The component configuration that supplies authentication
     *               information and the location of the TTS endpoint.
     */
    public SpokestackTTSService(SpeechConfig config) {
        this.client = new SpokestackTTSClient(callback);
        configure(config);
    }

    /**
     * Create a TTS service component that uses the provided TTS client. Used
     * for testing.
     *
     * @param config           The component configuration.
     * @param spokestackClient The TTS client.
     */
    SpokestackTTSService(SpeechConfig config,
                         SpokestackTTSClient spokestackClient) {
        this.client = spokestackClient;
        configure(config);
    }

    private void configure(SpeechConfig config) {
        String clientId = config.getString("spokestack-id");
        String clientSecret = config.getString("spokestack-secret");
        this.client.setCredentials(clientId, clientSecret);
        String ttsUrl = config.getString("spokestack-url", null);
        if (ttsUrl != null) {
            this.client.setTtsUrl(ttsUrl);
        }
    }

    @Override
    public void close() {
        this.client = null;
    }

    @Override
    public void synthesize(SynthesisRequest request) {
        this.client.synthesize(request);
    }

    /**
     * An internal callback class used to handle responses from the synthesis
     * service.
     */
    private class SpokestackCallback extends TTSCallback {
        @Override
        public void onFailure(@NonNull Call call, IOException e) {
            TTSEvent event = new TTSEvent(TTSEvent.Type.ERROR);
            event.setError(e);
            dispatch(event);
        }

        @Override
        public void onError(String message) {
            // no-op; we're throwing the error directly
        }

        @Override
        public void onSynthesisResponse(AudioResponse response) {
            TTSEvent event = new TTSEvent(TTSEvent.Type.AUDIO_AVAILABLE);
            event.setTtsResponse(response);
            dispatch(event);
        }
    }
}
