package io.spokestack.spokestack.tts;

import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Spokestack TTS client.
 *
 * <p>
 * The TTS client communicates with the Spokestack TTS service using simple REST
 * requests, receiving audio URLs that can be played with the Android media
 * player.
 * </p>
 */
public final class SpokestackTTSClient {
    private String ttsUrl = "https://core.pylon.com/speech/v1/tts/synthesize";
    private static final MediaType APPLICATION_JSON =
          MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private TTSCallback ttsCallback;
    private final Gson gson;
    private String ttsApiKey;

    /**
     * Create a new Spokestack TTS client. This client should be created once
     * and reused, as it contains an HTTP client with internal connection and
     * thread pools.
     *
     * @param callback The callback object used to deliver an audio URL when it
     *                 becomes available.
     */
    public SpokestackTTSClient(TTSCallback callback) {
        this(callback,
              new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
        );
    }

    /**
     * Create a new Spokestack TTS client with an API key and a provided HTTP
     * client. Used for testing.
     *
     * @param callback The callback object used to deliver an audio URL when it
     *                 becomes available.
     * @param client   The HTTP client to use for requests.
     */
    public SpokestackTTSClient(TTSCallback callback,
                               OkHttpClient client) {
        this.gson = new Gson();
        this.ttsCallback = callback;
        this.httpClient = client;
    }

    /**
     * Set the callback that should receive TTS responses.
     *
     * @param callback The TTS callback.
     */
    public void setTtsCallback(TTSCallback callback) {
        this.ttsCallback = callback;
    }

    /**
     * Set the URL used to synthesize text.
     *
     * @param url The URL to which synthesis requests should be sent.
     */
    public void setTtsUrl(String url) {
        this.ttsUrl = url;
    }

    /**
     * Set the API key used for synthesis requests.
     *
     * @param apiKey The API key to use for synthesis requests.
     */
    public void setApiKey(String apiKey) {
        this.ttsApiKey = apiKey;
    }

    /**
     * Synthesize text via the Spokestack TTS API using the default demo voice.
     * The synthesis request is asynchronous; the resulting audio URL for
     * playing back the synthesized speech is delivered to this object's
     * callback when it is available.
     *
     * @param text The text to synthesize.
     */
    public void synthesize(String text) {
        synthesize(text, "demo-male");
    }

    /**
     * Synthesize SSML via the Spokestack TTS API using the default demo voice.
     * The synthesis request is asynchronous; the resulting audio URL for
     * playing back the synthesized speech is delivered to this object's
     * callback when it is available.
     *
     * @param ssml The SSML to synthesize.
     */
    public void synthesize(SSML ssml) {
        synthesize(ssml, "demo-male");
    }

    /**
     * Synthesize text via the Spokestack TTS API. The synthesis request is
     * asynchronous; the resulting audio URL for playing back the synthesized
     * speech is delivered to this object's callback when it is available.
     *
     * @param text  The text to synthesize.
     * @param voice The voice to use for synthesis.
     */
    public void synthesize(String text, String voice) {
        HashMap<String, String> body = new HashMap<>();
        body.put("text", text);
        body.put("voice", voice);
        postSpeech(body);
    }

    /**
     * Synthesize SSML via the Spokestack TTS API. The synthesis request is
     * asynchronous; the resulting audio URL for playing back the synthesized
     * speech is delivered to this object's callback when it is available.
     *
     * @param ssml  The SSML to synthesize.
     * @param voice The voice to use for synthesis.
     */
    public void synthesize(SSML ssml, String voice) {
        HashMap<String, String> body = new HashMap<>();
        body.put("ssml", ssml.getText());
        body.put("voice", voice);
        postSpeech(body);
    }

    private void postSpeech(HashMap<String, String> speechBody) {
        if (this.ttsApiKey == null) {
            ttsCallback.onError("API key not provided");
            return;
        }
        if (this.ttsUrl == null) {
            ttsCallback.onError("TTS URL not provided");
            return;
        }

        RequestBody postBody = RequestBody.create(
              gson.toJson(speechBody), APPLICATION_JSON);

        Request request = new Request.Builder()
              .url(ttsUrl)
              .header("content-type", "application/json")
              .header("Authorization", "Key " + this.ttsApiKey)
              .post(postBody)
              .build();

        httpClient.newCall(request).enqueue(this.ttsCallback);
    }

}
