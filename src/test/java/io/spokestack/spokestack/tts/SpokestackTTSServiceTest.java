package io.spokestack.spokestack.tts;

import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.common.base.Objects;
import com.google.gson.Gson;
import io.spokestack.spokestack.SpeechConfig;
import okhttp3.Call;
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
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Call.class, Uri.class})
@PowerMockIgnore("javax.net.ssl.*")
public class SpokestackTTSServiceTest {

    private static final String AUDIO_URL =
          "https://spokestack.io/tts/test.mp3";
    private final Gson gson = new Gson();
    private SpokestackTTSClient client;
    private CallbackForwarder callbackForwarder;
    private Response validResponse;

    @Before
    public void before() throws Exception {
        mockStatic(Uri.class);
        when(Uri.parse(any())).thenReturn(mock(Uri.class));

        OkHttpClient httpClient = new OkHttpClient.Builder()
              .addInterceptor(new FakeResponder())
              .build();
        this.client = new SpokestackTTSClient(null, httpClient);
        this.callbackForwarder = new CallbackForwarder();
        this.client.setTtsCallback(this.callbackForwarder);

        Request request = new okhttp3.Request.Builder()
              .url("http://example.com/")
              .build();
        ResponseBody validBody = mock(ResponseBody.class);
        BufferedSource validSource = mock(BufferedSource.class);
        when(validSource.readString(any(Charset.class)))
              .thenReturn("{\"url\": \"" + AUDIO_URL + "\"}");
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
    public void testCleanup() {
        SpeechConfig config = new SpeechConfig();
        config.put("spokestack-key", "test");
        config.put("spokestack-url", "https://api.spokestack.io");
        SpokestackTTSService ttsService = new SpokestackTTSService(config,
              this.client);
        ttsService.close();
        assertThrows(NullPointerException.class,
              () -> ttsService.synthesize("test"));
    }

    @Test
    public void testSynthesize() throws InterruptedException {
        SpeechConfig config = new SpeechConfig();
        config.put("spokestack-key", "test");
        config.put("spokestack-url", "https://api.spokestack.io");
        SpokestackTTSService ttsService =
              new SpokestackTTSService(config, this.client);
        this.callbackForwarder.setTtsService(ttsService);

        // in a full TTS subsystem, the output component would be listening
        // to these events
        LinkedBlockingQueue<Uri> uriQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Throwable> errorQueue = new LinkedBlockingQueue<>();
        ttsService.addListener(event -> {
            switch (event.type) {
                case AUDIO_AVAILABLE:
                    uriQueue.add(event.getTtsResponse().getAudioUri());
                    break;
                case ERROR:
                    errorQueue.add(event.getError());
                    break;
            }
        });

        ttsService.synthesize("text");
        Uri uri = uriQueue.poll(1, TimeUnit.SECONDS);
        assertNotNull(uri);

        ttsService.synthesize(new SSML("<speak>ssml test</speak>"));
        uri = uriQueue.poll(1, TimeUnit.SECONDS);
        assertNotNull(uri);

        ttsService.synthesize("error");
        Throwable error = errorQueue.poll(1, TimeUnit.SECONDS);
        assertNotNull(error);
    }

    /**
     * Helper class to forward TTS client "responses" to the service under test
     * just as would happen in the real component. We need to circumvent the
     * normal construction here just so that we can inject the doctored HTTP
     * client.
     */
    private static class CallbackForwarder extends TTSCallback {
        private SpokestackTTSService ttsService;

        public void setTtsService(SpokestackTTSService ttsService) {
            this.ttsService = ttsService;
        }

        @Override
        public void onFailure(@NonNull Call call, IOException e) {
            ttsService.onFailure(call, e);
        }

        @Override
        public void onError(String message) {
            fail("mocked onError should never be reached");
        }

        @Override
        public void onUrlReceived(String url) {
            ttsService.onUrlReceived(url);
        }
    }

    /**
     * Test middleware that returns responses before actual HTTP requests
     * occur.
     */
    private class FakeResponder implements Interceptor {

        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request request = chain.request();
            RequestBody body = request.body();
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            Map json = gson.fromJson(buffer.readUtf8(), Map.class);

            if (Objects.equal(json.get("text"), "error")) {
                throw new IOException("test exc");
            }

            return validResponse;
        }
    }
}