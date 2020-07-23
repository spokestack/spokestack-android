package io.spokestack.spokestack.asr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.WebSocketListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class SpokestackCloudClientTest
      implements SpokestackCloudClient.Listener {
    private SpokestackCloudClient.Builder builder;
    private okhttp3.OkHttpClient http;
    private okhttp3.WebSocket socket;
    private String transcript;
    private float confidence;
    private Throwable error;

    @Before
    public void before() {
        this.builder = spy(SpokestackCloudClient.Builder.class);
        this.http = mock(okhttp3.OkHttpClient.class);
        this.socket = spy(okhttp3.WebSocket.class);

        // mock websocket connection
        when(http.newWebSocket(
              any(okhttp3.Request.class),
              any(okhttp3.WebSocketListener.class)
        )).thenReturn(this.socket);

        // mock http factory
        when(builder.getHttpClient())
              .thenReturn(http);

        this.transcript = null;
        this.error = null;
    }

    @Test
    public void testBuilder() {
        // missing credentials
        assertThrows(IllegalArgumentException.class, () -> {
            new SpokestackCloudClient.Builder()
                  .setLang("en")
                  .setSampleRate(8000)
                  .setListener(SpokestackCloudClientTest.this)
                  .build();
        });

        // invalid language
        assertThrows(IllegalArgumentException.class, () -> {
            new SpokestackCloudClient.Builder()
                  .setCredentials("ID", "secret")
                  .setSampleRate(8000)
                  .setListener(SpokestackCloudClientTest.this)
                  .build();
        });

        // invalid sample rate
        assertThrows(IllegalArgumentException.class, () -> {
            new SpokestackCloudClient.Builder()
                  .setCredentials("ID", "secret")
                  .setLang("en")
                  .setListener(SpokestackCloudClientTest.this)
                  .build();
        });

        // invalid listener
        assertThrows(IllegalArgumentException.class, () -> {
            new SpokestackCloudClient.Builder()
                  .setCredentials("ID", "secret")
                  .setLang("en")
                  .setSampleRate(8000)
                  .build();
        });

        // valid configuration
        this.builder
              .setCredentials("ID", "secret")
              .setLang("en")
              .setSampleRate(8000)
              .setListener(SpokestackCloudClientTest.this)
              .build()
              .close();

        assertNull(this.error);
    }

    @Test
    public void testSocketConnect() {
        final SpokestackCloudClient client = this.builder
              .setCredentials("ID", "secret")
              .setLang("en")
              .setSampleRate(8000)
              .setListener(SpokestackCloudClientTest.this)
              .build();

        // default connection
        assertFalse(client.isConnected());

        // valid connection
        client.connect();
        assertTrue(client.isConnected());

        // TODO failed auth
        // successful auth

        // failed reconnection
        assertThrows(IllegalStateException.class, client::connect);

        // valid disconnection
        client.disconnect();
        assertFalse(client.isConnected());

        // safe redisconnection
        client.disconnect();
        assertFalse(client.isConnected());

        client.close();
        assertNull(this.error);
    }

    @Test
    public void testSendAudio() {
        final ByteBuffer samples = ByteBuffer.allocateDirect(160);
        final SpokestackCloudClient client = this.builder
              .setCredentials("ID", "secret")
              .setLang("en")
              .setSampleRate(8000)
              .setListener(SpokestackCloudClientTest.this)
              .build();

        // invalid audio
        assertThrows(IllegalStateException.class, () -> client.sendAudio(samples));
        assertThrows(IllegalStateException.class, client::endAudio);

        client.connect();

        // empty audio
        client.endAudio();

        // valid audio
        for (int i = 0; i < 200; i++) {
            client.sendAudio(samples);
        }
        client.endAudio();

        // default event responses
        assertNull(this.transcript);
        assertNull(this.error);

        client.close();
        assertNull(this.error);
    }

    @Test
    public void testResponseEvents() {
        SpokestackCloudClient client = this.builder
              .setCredentials("ID", "secret")
              .setLang("en")
              .setSampleRate(8000)
              .setListener(SpokestackCloudClientTest.this)
              .build();

        client.connect();

        ArgumentCaptor<WebSocketListener> captor =
              ArgumentCaptor.forClass(okhttp3.WebSocketListener.class);
        verify(this.http)
              .newWebSocket(any(okhttp3.Request.class), captor.capture());
        okhttp3.WebSocketListener listener = captor.getValue();

        // socket close error
        listener.onClosed(this.socket, 1001, "failed");
        assertNull(this.transcript);
        assertEquals("close error 1001: failed", this.error.getMessage());
        this.error = null;

        // general error
        listener.onFailure(this.socket, new Exception("failed"), null);
        assertNull(this.transcript);
        assertEquals("failed", this.error.getMessage());
        this.error = null;

        // valid close
        listener.onClosed(this.socket, 1000, "goodbye");
        assertNull(this.transcript);
        assertNull(this.error);

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();

        // error
        SpokestackASRResponse response = new SpokestackASRResponse(
              "error",
              "invalid_request",
              true,
              new SpokestackASRResponse.Hypothesis[0]
        );
        listener.onMessage(this.socket, gson.toJson(response));
        String err = String.format("ASR error: %s", response.error);
        assertNull(this.transcript);
        assertEquals(0.0f, this.confidence, 1e-5);
        assertEquals(err, this.error.getMessage());
        this.error = null;

        // no hypotheses
        response = new SpokestackASRResponse(
              "ok", null, true, new SpokestackASRResponse.Hypothesis[0]
        );
        listener.onMessage(this.socket, gson.toJson(response));
        assertNull(this.transcript);
        assertEquals(0.0f, this.confidence, 1e-5);
        assertNull(this.error);

        // non-final recognition
        SpokestackASRResponse.Hypothesis hypothesis =
              new SpokestackASRResponse.Hypothesis("test", 0.75f);
        SpokestackASRResponse.Hypothesis[] hypotheses = {hypothesis};
        response = new SpokestackASRResponse("ok", null, false, hypotheses);
        listener.onMessage(this.socket, gson.toJson(response));
        assertNull(this.transcript);
        assertEquals(0.0f, this.confidence, 1e-5);
        assertNull(this.error);

        // set a fake value to make sure it gets updated on a final recognition
        this.confidence = 1.0f;

        // final recognition
        response = new SpokestackASRResponse("ok", null, true, hypotheses);
        listener.onMessage(this.socket, gson.toJson(response));
        assertEquals("test", this.transcript);
        assertEquals(0.75f, this.confidence, 1e-5);
        assertNull(this.error);
    }

    public void onSpeech(String transcript, float confidence) {
        this.transcript = transcript;
        this.confidence = confidence;
    }

    public void onError(Throwable e) {
        this.error = e;
    }
}
