package io.spokestack.spokestack.tts;

import android.content.Context;
import android.net.Uri;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechOutput;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TTSTestUtils {
    public static final String AUDIO_URL = "https://spokestack.io/tts/test.mp3";

    /**
     * JSON for a GraphQL response to {@code synthesizeText}.
     */
    public static final String TEXT_JSON =
          "{\"data\": {\"synthesizeText\": {\"url\": \""
                + AUDIO_URL + "\"}}}";

    /**
     * JSON for a GraphQL SSML error.
     */
    public static final String ERROR_JSON =
          "{\"data\": null, "
                + "\"errors\": [{\"message\": \"invalid_ssml\" }]}";

    /**
     * Creates an OKHttp {@code Response} with the specified body.
     */
    public static Response createHttpResponse(Request request, String body)
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

    public static class Service extends TTSService {

        List<String> calls = new ArrayList<>();
        CountDownLatch latch;

        public Service(SpeechConfig config) {
            String key = config.getString("spokestack-id", "default");
            if (!key.equals("default")) {
                fail("custom client ID should not be set by tests");
            }
            Object latch = config.getParams().get("service-latch");
            this.latch = (CountDownLatch) latch;
        }

        @Override
        public void synthesize(SynthesisRequest request) {
            this.calls.add("synthesize");
            try {
                deliverResponse(request);
            } catch (InterruptedException e) {
                fail();
            }
        }

        public void deliverResponse(SynthesisRequest request)
              throws InterruptedException {
            Thread.sleep(10);
            TTSEvent synthesisComplete =
                  new TTSEvent(TTSEvent.Type.AUDIO_AVAILABLE);
            Map<String, Object> responseMeta = new HashMap<>();
            for (Map.Entry<String, String> entry : request.metadata.entrySet()) {
                responseMeta.put(entry.getKey(), entry.getValue());
            }
            AudioResponse response = new AudioResponse(responseMeta, Uri.EMPTY);
            synthesisComplete.setTtsResponse(response);
            this.calls.add("deliver");
            dispatch(synthesisComplete);
            if (this.latch != null) {
                this.latch.countDown();
            }
        }

        @Override
        public void close() {
        }
    }

    public static class Output extends SpeechOutput {

        LinkedBlockingQueue<String> events;

        @SuppressWarnings("unused")
        public Output(SpeechConfig config) {
            this.events = new LinkedBlockingQueue<>();
        }

        public void setEventQueue(LinkedBlockingQueue<String> events) {
            this.events = events;
        }

        public LinkedBlockingQueue<String> getEvents() {
            return events;
        }

        @Override
        public void audioReceived(AudioResponse response) {
            this.events.add("audioReceived");
        }

        @Override
        public void stopPlayback() {
            this.events.add("stop");
        }

        @Override
        public void setAndroidContext(Context appContext) {
        }

        @Override
        public void close() {
            throw new RuntimeException("can't close won't close");
        }
    }
}
