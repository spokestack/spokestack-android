package io.spokestack.spokestack.tts;

import android.net.Uri;
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
 *         <b>spokestack-key</b> (string): The API key used for synthesis
 *         requests.
 *     </li>
 *     <li>
 *         <b>spokestack-url</b> (string): The URL used for synthesis
 *         requests. Defaults to the synthesis URL active at the time this
 *         version of the library was published.
 *     </li>
 * </ul>
 */
public final class SpokestackTTSService extends TTSCallback
      implements TTSService {
    private SpokestackTTSClient client;

    /**
     * Creates a new TTS service component.
     *
     * @param config The component configuration that supplies authentication
     *               information and the location of the TTS endpoint.
     */
    public SpokestackTTSService(SpeechConfig config) {
        this.client = new SpokestackTTSClient(this);
        configure(config);
    }

    /**
     * Create a TTS service component that uses the provided TTS client. Used
     * for testing.
     *
     * @param config The component configuration.
     * @param spokestackClient The TTS client.
     */
    SpokestackTTSService(SpeechConfig config,
                         SpokestackTTSClient spokestackClient) {
        this.client = spokestackClient;
        configure(config);
    }

    private void configure(SpeechConfig config) {
        String apiKey = config.getString("spokestack-key");
        this.client.setApiKey(apiKey);
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
    public void synthesize(CharSequence text) {
        this.client.synthesize(String.valueOf(text));
    }

    @Override
    public void synthesize(SSML ssml) {
        this.client.synthesize(String.valueOf(ssml));
    }

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
    public void onUrlReceived(String url) {
        TTSEvent event = new TTSEvent(TTSEvent.Type.AUDIO_AVAILABLE);
        Uri audioUri = Uri.parse(url);
        AudioResponse response = new AudioResponse(audioUri);
        event.setTtsResponse(response);
        dispatch(event);
    }
}
