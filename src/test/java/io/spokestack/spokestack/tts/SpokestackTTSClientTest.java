package io.spokestack.spokestack.tts;

import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SpokestackTTSClientTest {
    private Call invalidCall;
    private Call errorCall;
    private Call validCall;
    private static final String AUDIO_URL =
          "https://spokestack.io/tts/test.mp3";
    private OkHttpClient httpClient;

    @Before
    public void before() throws IOException {
        setUpCallMocks();
        httpClient = mock(OkHttpClient.class);
        when(httpClient.newCall(any()))
              .thenAnswer(new ApiKeyedCall());
    }

    private void setUpCallMocks() throws IOException {
        Request request = new okhttp3.Request.Builder()
              .url("http://example.com/")
              .build();

        invalidCall = mock(Call.class);
        Response invalidResponse = new Response.Builder()
              .request(request)
              .protocol(okhttp3.Protocol.HTTP_1_1)
              .code(403)
              .message("Unauthorized")
              .build();
        when(invalidCall.execute()).thenReturn(invalidResponse);
        errorCall = mock(Call.class);
        Response errorResponse = new Response.Builder()
              .request(request)
              .protocol(okhttp3.Protocol.HTTP_1_1)
              .code(419)
              .message("Unacceptable")
              .build();
        when(errorCall.execute()).thenReturn(errorResponse);
        validCall = mock(Call.class);
        ResponseBody validBody = mock(ResponseBody.class);
        BufferedSource validSource = mock(BufferedSource.class);
        when(validSource.readString(any(Charset.class)))
              .thenReturn("{\"url\": \"" + AUDIO_URL + "\"}");
        when(validBody.source()).thenReturn(validSource);
        Response validResponse = new Response.Builder()
              .request(request)
              .protocol(okhttp3.Protocol.HTTP_1_1)
              .code(200)
              .message("OK")
              .body(validBody)
              .build();
        when(validCall.execute()).thenReturn(validResponse);
    }

    @Test
    public void testSpeak() {
        // no api key
        SpokestackTTSClient client = new SpokestackTTSClient(
              new TestCallback("API key not provided"),
              httpClient);
        client.setApiKey("invalid");

        // assertions are handled in the callback
        client.speak("text");

        // invalid api key
        client = new SpokestackTTSClient(
              new TestCallback("Invalid API key"),
              httpClient);
        client.setApiKey("invalid");
        client.speak("text");

        // invalid ssml
        client = new SpokestackTTSClient(
              new TestCallback("Synthesis error: HTTP 419"),
              httpClient);
        client.setApiKey("key");
        SSML invalidSsml = new SSML("just text");
        client.speak(invalidSsml);


        // no TTS URL
        client = new SpokestackTTSClient(
              new TestCallback("TTS URL not provided"),
              httpClient);
        client.setApiKey("key");
        client.setTtsUrl(null);
        client.speak("text");

        // valid text request
        client = new SpokestackTTSClient(
              new TestCallback(null),
              httpClient);
        client.setApiKey("key");

        client.speak("text");

        // valid ssml request
        SSML validSSML = new SSML("<speak>aloha</speak>");
        client.speak(validSSML);
    }

    class ApiKeyedCall implements Answer<Call> {
        private Gson gson;

        public ApiKeyedCall() {
            this.gson = new Gson();
        }

        @Override
        public Call answer(InvocationOnMock invocationOnMock) throws IOException {
            Request request = invocationOnMock.getArgument(0);
            if (hasInvalidKey(request)) {
                return invalidCall;
            }
            if (hasInvalidBody(request)) {
                return errorCall;
            }
            return validCall;
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

    static class TestCallback extends TTSCallback {
        private String errorMessage;

        public TestCallback(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public void onError(String message) {
            assertEquals(this.errorMessage, message);
        }

        @Override
        public void onUrlReceived(String url) {
            if (this.errorMessage != null) {
                fail("Error expected");
            }
            assertNotNull(url);
        }
    }
}
