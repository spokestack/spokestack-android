package io.spokestack.spokestack.tts;

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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpokestackTTSClientTest {
    private Response invalidResponse;
    private Response errorResponse;
    private Response validResponse;
    private static final String AUDIO_URL =
          "https://spokestack.io/tts/test.mp3";
    private static final Gson gson = new Gson();
    private OkHttpClient httpClient;

    @Before
    public void before() throws IOException {
        mockResponses();
        httpClient = new OkHttpClient.Builder()
              .addInterceptor(new FakeResponder())
              .build();
    }

    private void mockResponses() throws IOException {
        Request request = new okhttp3.Request.Builder()
              .url("http://example.com/")
              .build();

        invalidResponse = new Response.Builder()
              .request(request)
              .protocol(okhttp3.Protocol.HTTP_1_1)
              .code(403)
              .message("Unauthorized")
              .body(mock(ResponseBody.class))
              .build();
        errorResponse = new Response.Builder()
              .request(request)
              .protocol(okhttp3.Protocol.HTTP_1_1)
              .code(419)
              .message("Unacceptable")
              .body(mock(ResponseBody.class))
              .build();
        ResponseBody validBody = mock(ResponseBody.class);
        BufferedSource validSource = mock(BufferedSource.class);
        when(validSource.readString(any(Charset.class)))
              .thenReturn("{\"url\": \"" + AUDIO_URL + "\"," +
                    "\"requestId\": \"123\"}");
        when(validBody.source()).thenReturn(validSource);
        validResponse = new Response.Builder()
              .request(request)
              .protocol(okhttp3.Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body(validBody)
              .build();
    }

    @Test
    public void testSpeak() throws InterruptedException {
        // no api key
        TestCallback callback = new TestCallback("API key not provided");
        SpokestackTTSClient client =
              new SpokestackTTSClient(callback, httpClient);

        client.synthesize("text");
        assertTrue(callback.errorReceived);

        // invalid api key
        CountDownLatch latch = new CountDownLatch(1);
        callback = new TestCallback("Invalid API key", latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setApiKey("invalid");
        client.synthesize("text");
        latch.await(1, TimeUnit.SECONDS);
        assertTrue(callback.errorReceived);

        // invalid ssml
        latch = new CountDownLatch(1);
        callback = new TestCallback("Synthesis error: HTTP 419", latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setApiKey("key");
        SSML invalidSsml = new SSML("just text");
        client.synthesize(invalidSsml);
        latch.await(1, TimeUnit.SECONDS);
        assertTrue(callback.errorReceived);


        // no TTS URL
        latch = new CountDownLatch(1);
        callback = new TestCallback("TTS URL not provided", latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setApiKey("key");
        client.setTtsUrl(null);
        client.synthesize("text");
        latch.await(1, TimeUnit.SECONDS);
        assertTrue(callback.errorReceived);

        // valid text request
        latch = new CountDownLatch(1);
        callback = new TestCallback(null, latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setApiKey("key");

        client.synthesize("text");
        latch.await(1, TimeUnit.SECONDS);
        assertTrue(callback.urlReceived);

        // valid ssml request
        latch = new CountDownLatch(1);
        callback = new TestCallback(null, latch);
        client = new SpokestackTTSClient(callback, httpClient);
        client.setApiKey("key");

        SSML validSSML = new SSML("<speak>aloha</speak>");
        client.synthesize(validSSML);
        latch.await(1, TimeUnit.SECONDS);
        assertTrue(callback.urlReceived);
    }

    static class TestCallback extends TTSCallback {
        private String errorMessage;
        private CountDownLatch countDownLatch;
        boolean errorReceived = false;
        boolean urlReceived = false;

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
        public void onUrlReceived(String url) {
            if (this.errorMessage != null) {
                fail("Error expected");
            }
            urlReceived = true;
            countDownLatch.countDown();
        }
    }

    /**
     * Test middleware that returns responses before actual HTTP requests occur,
     * varying the response based on predetermined request parameters.
     */
    private class FakeResponder implements Interceptor {

        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request request = chain.request();

            if (hasInvalidKey(request)) {
                return invalidResponse;
            }
            if (hasInvalidBody(request)) {
                return errorResponse;
            }
            return validResponse;
        }

        private boolean hasInvalidKey(Request request) {
            return Objects.equals(request.header("Authorization"),
                  "Key " + "invalid");
        }

        private boolean hasInvalidBody(Request request) throws IOException {
            RequestBody body = request.body();
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            Map json = gson.fromJson(buffer.readUtf8(), Map.class);
            String ssml = (String) json.get("ssml");
            return ssml != null && !ssml.startsWith("<speak>");
        }
    }
}
