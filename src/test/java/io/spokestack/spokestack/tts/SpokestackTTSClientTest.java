package io.spokestack.spokestack.tts;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpokestackTTSClientTest {
    private Response invalidResponse;
    private static final String AUDIO_URL =
          "https://spokestack.io/tts/test.mp3";
    private static final Gson gson = new Gson();
    private OkHttpClient httpClient;

    @Before
    public void before() {
        mockResponses();
        httpClient = new OkHttpClient.Builder()
              .addInterceptor(new FakeResponder())
              .build();
    }

    private void mockResponses() {
        Request request = new okhttp3.Request.Builder()
              .url("http://example.com/")
              .build();

        invalidResponse = new Response.Builder()
              .request(request)
              .protocol(okhttp3.Protocol.HTTP_1_1)
              .code(401)
              .message("Unauthorized")
              .body(mock(ResponseBody.class))
              .build();
    }

    @Test
    public void testConfig() throws Exception {
        // no API id
        TestCallback callback = new TestCallback("API ID not provided");
        SpokestackTTSClient client =
              new SpokestackTTSClient(callback, httpClient);

        SynthesisRequest request = new SynthesisRequest.Builder("text").build();
        client.synthesize(request);
        assertTrue(callback.errorReceived);

        // no API secret
        callback = new TestCallback("API secret not provided");
        client = new SpokestackTTSClient(callback, httpClient);
        client.setCredentials("valid", null);

        client.synthesize(request);
        assertTrue(callback.errorReceived);

        // invalid API secret
        CountDownLatch latch = new CountDownLatch(1);
        callback = new TestCallback("Invalid credentials", latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setCredentials("invalid", "invalider");
        client.synthesize(request);
        latch.await(1, TimeUnit.SECONDS);
        assertTrue(callback.errorReceived);

        // invalid ssml
        latch = new CountDownLatch(1);
        callback = new TestCallback("Synthesis error: invalid_ssml", latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setCredentials("id", "secret");
        SynthesisRequest invalidSSML = new SynthesisRequest.Builder("just text")
              .withMode(SynthesisRequest.Mode.SSML).build();
        client.synthesize(invalidSSML);
        latch.await(1, TimeUnit.SECONDS);
        assertTrue(callback.errorReceived);

        // no TTS URL
        latch = new CountDownLatch(1);
        callback = new TestCallback("TTS URL not provided", latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setCredentials("id", "secret");
        client.setTtsUrl(null);
        client.synthesize(request);
        latch.await(1, TimeUnit.SECONDS);
        assertTrue(callback.errorReceived);
    }

    @Test
    public void testRequestSigning() {
        CountDownLatch latch = new CountDownLatch(1);
        TestCallback callback = new TestCallback(null, latch);
        SpokestackTTSClient client = new SpokestackTTSClient(callback, httpClient);
        String id = "f0bc990c-e9db-4a0c-a2b1-6a6395a3d97e";
        String secret =
              "5BD5483F573D691A15CFA493C1782F451D4BD666E39A9E7B2EBE287E6A72C6B6";
        client.setCredentials(id, secret);

        String body = "{\"query\": "
              + "\"query AndroidSynthesize($voice:String!, $text:String!) {"
              + "synthesizeText(voice: $voice, text: $text) {url}}\", "
              + "\"variables\": {\"voice\": \"demo-male\", \"text\": \"test\""
              + "}}";
        String sig = client.signRequest(body);
        assertEquals("ZqrTG+aiIYJKgHB63HCmXCLj0acUEi92d/b2au2WdEM=", sig);
    }

    @Test
    public void testSpeak() throws Exception {
        // valid text request
        CountDownLatch latch = new CountDownLatch(1);
        TestCallback callback = new TestCallback(null, latch);
        SpokestackTTSClient client = new SpokestackTTSClient(callback, httpClient);
        client.setCredentials("id", "secret");

        SynthesisRequest request = new SynthesisRequest.Builder("text").build();
        client.synthesize(request);
        latch.await(1, TimeUnit.SECONDS);
        assertNotNull(callback.audioResponse);

        // valid markdown request
        latch = new CountDownLatch(1);
        callback = new TestCallback(null, latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setCredentials("id", "secret");

        SynthesisRequest validMarkdown =
              new SynthesisRequest.Builder(
                    "Hello! [1s] Can you spare (5)[number] minutes?")
                    .withMode(SynthesisRequest.Mode.MARKDOWN)
                    .build();
        client.synthesize(validMarkdown);
        latch.await(1, TimeUnit.SECONDS);
        assertNotNull(callback.audioResponse);

        // valid ssml request
        latch = new CountDownLatch(1);
        callback = new TestCallback(null, latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setCredentials("id", "secret");
        HashMap<String, String> metadata = new HashMap<>();
        String requestId = "abce153193";
        metadata.put("id", requestId);

        SynthesisRequest validSSML =
              new SynthesisRequest.Builder("<speak>aloha</speak>")
                    .withMode(SynthesisRequest.Mode.SSML)
                    .withData(metadata)
                    .build();
        client.synthesize(validSSML);
        latch.await(1, TimeUnit.SECONDS);
        AudioResponse response = callback.audioResponse;
        assertEquals(requestId, response.getMetadata().get("id"));
    }

    static class TestCallback extends TTSCallback {
        private final String errorMessage;
        private final CountDownLatch countDownLatch;
        boolean errorReceived = false;
        AudioResponse audioResponse;

        public TestCallback(String errorMessage) {
            this.errorMessage = errorMessage;
            this.countDownLatch = new CountDownLatch(1);
        }

        public TestCallback(String errorMessage,
                            CountDownLatch countDownLatch) {
            this.errorMessage = errorMessage;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void onError(String message) {
            assertEquals(this.errorMessage, message);
            errorReceived = true;
            countDownLatch.countDown();
        }

        @Override
        protected AudioResponse createAudioResponse(
              @NonNull SpokestackSynthesisResponse response,
              @Nullable String requestId) {
            HashMap<String, Object> metadata = new HashMap<>();
            metadata.put("id", requestId);
            return new AudioResponse(metadata, Uri.EMPTY);
        }

        @Override
        public void onSynthesisResponse(AudioResponse response) {
            if (this.errorMessage != null) {
                fail("Error expected");
            }
            this.audioResponse = response;
            countDownLatch.countDown();
        }
    }

    /**
     * Test middleware that returns responses before actual HTTP requests occur,
     * varying the response based on predetermined request parameters.
     */
    private class FakeResponder implements Interceptor {

        private static final String TEXT_JSON =
              "{\"data\": {\"synthesizeText\": {\"url\": \""
                    + AUDIO_URL + "\"}}}";

        private static final String ERROR_JSON =
              "{\"data\": null, "
                    + "\"errors\": [{\"message\": \"invalid_ssml\" }]}";

        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request request = chain.request();

            if (hasInvalidId(request)) {
                return invalidResponse;
            }
            if (hasInvalidBody(request)) {
                // simulate a GraphQL error, which are wrapped in HTTP 200s
                return createResponse(request, ERROR_JSON);
            }
            return createResponse(request, TEXT_JSON);
        }

        private Response createResponse(Request request, String body)
              throws IOException {
            ResponseBody responseBody = mock(ResponseBody.class);
            BufferedSource source = mock(BufferedSource.class);
            when(source.readString(any(Charset.class))).thenReturn(body);
            when(responseBody.source()).thenReturn(source);
            Response.Builder builder = new Response.Builder()
                  .request(request)
                  .protocol(okhttp3.Protocol.HTTP_1_1)
                  .code(200)
                  .message("OK")
                  .body(responseBody);

            String requestId = request.header("x-request-id");
            if (requestId != null) {
                builder.header("x-request-id", requestId);
            }

            return builder.build();
        }

        private boolean hasInvalidId(Request request) {
            String authHeader = request.header("Authorization");
            return authHeader != null && authHeader.contains("invalid:");
        }

        private boolean hasInvalidBody(Request request) throws IOException {
            RequestBody body = request.body();
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            String bodyText = buffer.readUtf8();
            Map json = gson.fromJson(bodyText, Map.class);
            Map variables = (Map) json.get("variables");
            String ssml = (String) variables.get("ssml");
            return ssml != null && !ssml.startsWith("<speak>");
        }
    }
}
