package io.spokestack.spokestack.tts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.spokestack.spokestack.util.Base64;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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
    private static final String HMAC_TYPE = "HmacSHA256";
    private static final String GRAPHQL_QUERY =
          "query AndroidSynthesize($voice: String!, $%1$s: String!) {"
                + "%2$s(voice: $voice, %1$s: $%1$s) {"
                + "url"
                + "}}";

    private final OkHttpClient httpClient;
    private TTSCallback ttsCallback;
    private final Gson gson;
    private String ttsClientKey;
    private String ttsClientSecret;

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
     * Set the API key used for synthesis requests.
     *
     * @param apiKey       The API key to use for synthesis requests.
     * @param clientSecret The client secret used to authorize synthesis
     *                     requests.
     */
    public void setCredentials(String apiKey, String clientSecret) {
        this.ttsClientKey = apiKey;
        this.ttsClientSecret = clientSecret;
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
        if (this.ttsClientKey == null) {
            ttsCallback.onError("client key not provided");
            return;
        }
        if (this.ttsClientSecret == null) {
            ttsCallback.onError("client secret not provided");
            return;
        }
        if (this.ttsUrl == null) {
            ttsCallback.onError("TTS URL not provided");
            return;
        }

        String bodyJson = gson.toJson(variables);
        String fullBody =
              "{\"query\": \"" + queryString + "\", "
                    + "\"variables\": " + bodyJson + "}";
        RequestBody postBody = RequestBody.create(fullBody, APPLICATION_JSON);

        Request.Builder builder = new Request.Builder();

        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder = builder.addHeader(header.getKey(), header.getValue());
        }

        String authHeader = signRequest(fullBody);
        if (authHeader == null) {
            // the error is dispatched to the callback by signRequest
            return;
        }

        Request request = builder
              .url(ttsUrl)
              .header("Content-Type", "application/json")
              .header("Authorization",
                    "Spokestack " + ttsClientKey + ":" + authHeader)
              .post(postBody)
              .build();

        httpClient.newCall(request).enqueue(this.ttsCallback);
    }

    String signRequest(String body) {
        String base64Signature = null;
        try {
            Mac hmacAlgo = Mac.getInstance(HMAC_TYPE);
            byte[] keyBytes =
                  this.ttsClientSecret.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_TYPE);
            hmacAlgo.init(keySpec);
            byte[] macData = hmacAlgo.doFinal(
                  body.getBytes(StandardCharsets.UTF_8));
            base64Signature = Base64.encode(macData);
        } catch (NoSuchAlgorithmException e) {
            this.ttsCallback.onError("Invalid HMAC algorithm");
        } catch (InvalidKeyException e) {
            this.ttsCallback.onError("Invalid client key");
        }

        return base64Signature;
    }
}
