package io.spokestack.spokestack.tts;

import io.spokestack.spokestack.ComponentConfig;
import io.spokestack.spokestack.TTSEvent;
import io.spokestack.spokestack.TTSService;

import java.net.URI;

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

    public SpokestackTTSService(ComponentConfig config) {
        this.client = new SpokestackTTSClient(this);
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
    public void onError(String message) {
        TTSEvent event = new TTSEvent(TTSEvent.Type.ERROR);
        event.setError(new Exception(message));
        dispatch(event);
    }

    @Override
    public void onUrlReceived(String url) {
        TTSEvent event = new TTSEvent(TTSEvent.Type.AUDIO_AVAILABLE);
        event.setAudioUri(URI.create(url));
        dispatch(event);
    }
}
