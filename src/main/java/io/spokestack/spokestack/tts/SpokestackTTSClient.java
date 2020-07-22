package io.spokestack.spokestack.tts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.spokestack.spokestack.util.Crypto;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.HashMap;
import java.util.Map;
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
    private String ttsUrl = "https://api.spokestack.io/v1";
    private static final MediaType APPLICATION_JSON =
          MediaType.parse("application/json");
    private static final String GRAPHQL_QUERY =
          "query AndroidSynthesize($voice: String!, $%1$s: String!) {"
                + "%2$s(voice: $voice, %1$s: $%1$s) {"
                + "url"
                + "}}";

    private final OkHttpClient httpClient;
    private TTSCallback ttsCallback;
    private final Gson gson;
    private String ttsApiId;
    private String ttsApiSecret;

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
     * Create a new Spokestack TTS client with an API ID and a provided HTTP
     * client. Used for testing.
     *
     * @param callback The callback object used to deliver an audio URL when it
     *                 becomes available.
     * @param client   The HTTP client to use for requests.
     */
    public SpokestackTTSClient(TTSCallback callback,
                               OkHttpClient client) {
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
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
     * Set the API ID used for synthesis requests.
     *
     * @param apiId     The ID used for synthesis requests.
     * @param apiSecret The secret key used to sign synthesis requests.
     */
    public void setCredentials(String apiId, String apiSecret) {
        this.ttsApiId = apiId;
        this.ttsApiSecret = apiSecret;
    }

    /**
     * Synthesize speech via the Spokestack TTS API. The synthesis request is
     * asynchronous; the resulting audio URL for playing back the synthesized
     * speech is delivered to this object's callback when it is available.
     *
     * @param request The request object representing the text to be synthesized
     *                and any additional metadata.
     */
    public void synthesize(SynthesisRequest request) {
        HashMap<String, String> headers = new HashMap<>();
        String requestId = request.metadata.get("id");
        if (requestId != null) {
            headers.put("x-request-id", requestId);
        }

        HashMap<String, String> variables = new HashMap<>();
        String param = "text";
        String method = "synthesizeText";
        switch (request.mode) {
            case TEXT:
                variables.put("text", String.valueOf(request.text));
                break;
            case MARKDOWN:
                variables.put("markdown", String.valueOf(request.text));
                param = "markdown";
                method = "synthesizeMarkdown";
                break;
            case SSML:
                variables.put("ssml", String.valueOf(request.text));
                param = "ssml";
                method = "synthesizeSsml";
                break;
            default:
                break;
        }
        variables.put("voice", request.voice);
        String queryString = String.format(GRAPHQL_QUERY, param, method);
        postSpeech(headers, queryString, variables);
    }

    private void postSpeech(Map<String, String> headers,
                            String queryString,
                            Map<String, String> variables) {
        if (this.ttsApiId == null) {
            ttsCallback.onError("API ID not provided");
            return;
        }
        if (this.ttsApiSecret == null) {
            ttsCallback.onError("API secret not provided");
            return;
        }
        if (this.ttsUrl == null) {
            ttsCallback.onError("TTS URL not provided");
            return;
        }

        Map<String, Object> fullBody = new HashMap<>();
        fullBody.put("query", queryString);
        fullBody.put("variables", variables);
        String fullBodyJson = gson.toJson(fullBody);
        RequestBody postBody =
              RequestBody.create(fullBodyJson, APPLICATION_JSON);

        Request.Builder builder = new Request.Builder();

        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder = builder.addHeader(header.getKey(), header.getValue());
        }

        String authHeader = signRequest(fullBodyJson);
        if (authHeader == null) {
            // the error is dispatched to the callback by signRequest
            return;
        }

        Request request = builder
              .url(ttsUrl)
              .header("Content-Type", "application/json")
              .header("Authorization",
                    "Spokestack " + ttsApiId + ":" + authHeader)
              .post(postBody)
              .build();

        httpClient.newCall(request).enqueue(this.ttsCallback);
    }

    String signRequest(String body) {
        String base64Signature = null;
        try {
            base64Signature = Crypto.signBody(body, this.ttsApiSecret);
        } catch (IllegalArgumentException e) {
            this.ttsCallback.onError("Invalid API secret");
        }

        return base64Signature;
    }
}
