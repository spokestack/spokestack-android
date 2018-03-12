package com.pylon.spokestack.microsoft;

import java.util.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.junit.Test;
import org.junit.Before;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

public class BingSpeechClientTest implements BingSpeechClient.Listener {
    private BingSpeechClient.Builder builder;
    private okhttp3.OkHttpClient http;
    private okhttp3.WebSocket socket;
    private String transcript;
    private Throwable error;

    @Before
    public void before() throws Exception {
        this.builder = spy(BingSpeechClient.Builder.class);
        this.http = mock(okhttp3.OkHttpClient.class);
        this.socket = spy(okhttp3.WebSocket.class);

        // mock authentication response
        okhttp3.Request authRequest = new okhttp3.Request.Builder()
            .url("http://example.com/")
            .build();

        okhttp3.ResponseBody authBody = mock(okhttp3.ResponseBody.class);
        okio.BufferedSource authSource = mock(okio.BufferedSource.class);
        when(authSource.readString(any(Charset.class)))
            .thenReturn("token");
        when(authBody.source())
            .thenReturn(authSource);
        okhttp3.Response authResponse = new okhttp3.Response.Builder()
            .request(authRequest)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(authBody)
            .build();

        okhttp3.Call authCall = mock(okhttp3.Call.class);
        when(authCall.execute())
            .thenReturn(authResponse);
        when(http.newCall(any(okhttp3.Request.class)))
            .thenReturn(authCall);

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
    public void testBuilder() throws Exception {
        // invalid api key
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() throws Exception {
                new BingSpeechClient.Builder()
                    .setLocale("en-US")
                    .setSampleRate(8000)
                    .setListener(BingSpeechClientTest.this)
                    .build();
            }
        });

        // invalid locale
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() throws Exception {
                new BingSpeechClient.Builder()
                    .setApiKey("secret")
                    .setSampleRate(8000)
                    .setListener(BingSpeechClientTest.this)
                    .build();
            }
        });

        // invalid sample rate
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() throws Exception {
                new BingSpeechClient.Builder()
                    .setApiKey("secret")
                    .setLocale("en-US")
                    .setListener(BingSpeechClientTest.this)
                    .build();
            }
        });

        // invalid listener
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() throws Exception {
                new BingSpeechClient.Builder()
                    .setApiKey("secret")
                    .setLocale("en-US")
                    .setSampleRate(8000)
                    .build();
            }
        });

        // unauthorized
        assertThrows(Exception.class, new Executable() {
            public void execute() throws Exception {
                new BingSpeechClient.Builder()
                    .setApiKey("invalid")
                    .setLocale("en-US")
                    .setSampleRate(8000)
                    .setListener(BingSpeechClientTest.this)
                    .build();
            }
        });

        // valid configuration
        this.builder
            .setApiKey("secret")
            .setLocale("en-US")
            .setSampleRate(8000)
            .setListener(BingSpeechClientTest.this)
            .build()
            .close();

        assertEquals(null, this.error);
    }

    @Test
    public void testSocketConnect() throws Exception {
        final BingSpeechClient client = this.builder
            .setApiKey("secret")
            .setLocale("en-US")
            .setSampleRate(8000)
            .setListener(BingSpeechClientTest.this)
            .build();

        // default connection
        assertFalse(client.isConnected());

        // valid connection
        client.connect();
        assertTrue(client.isConnected());

        // failed reconnection
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() throws Exception { client.connect(); }
        });

        // valid disconnection
        client.disconnect();
        assertFalse(client.isConnected());

        // safe redisconnection
        client.disconnect();
        assertFalse(client.isConnected());

        client.close();
        assertEquals(null, this.error);
    }

    @Test
    public void testSendAudio() throws Exception {
        final ByteBuffer samples = ByteBuffer.allocateDirect(160);
        final BingSpeechClient client = this.builder
            .setApiKey("secret")
            .setLocale("en-US")
            .setSampleRate(8000)
            .setListener(BingSpeechClientTest.this)
            .build();

        // invalid audio
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() throws Exception {
                client.beginAudio();
            }
        });
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() throws Exception {
                client.sendAudio(samples);
            }
        });
        assertThrows(IllegalStateException.class, new Executable() {
            public void execute() throws Exception {
                client.endAudio();
            }
        });

        client.connect();

        // empty audio
        client.beginAudio();
        client.endAudio();

        Thread.sleep(1000);

        // valid audio
        client.beginAudio();
        for (int i = 0; i < 200; i++)
            client.sendAudio(samples);
        client.endAudio();

        // default event responses
        assertEquals(null, this.transcript);
        assertEquals(null, this.error);

        client.close();
        assertEquals(null, this.error);
    }

    @Test
    public void testResponseEvents() throws Exception {
        BingSpeechClient client = this.builder
            .setApiKey("secret")
            .setLocale("en-US")
            .setSampleRate(8000)
            .setListener(BingSpeechClientTest.this)
            .build();
        String message;

        client.connect();
        client.beginAudio();

        ArgumentCaptor<okhttp3.WebSocketListener> captor =
            ArgumentCaptor.forClass(okhttp3.WebSocketListener.class);
        verify(this.http)
            .newWebSocket(any(okhttp3.Request.class), captor.capture());
        okhttp3.WebSocketListener listener = captor.getValue();

        // socket close error
        listener.onClosed(this.socket, 1001, "failed");
        assertEquals(null, this.transcript);
        assertEquals("1001: failed", this.error.getMessage());
        this.error = null;

        // general error
        listener.onFailure(this.socket, new Exception("failed"), null);
        assertEquals(null, this.transcript);
        assertEquals("failed", this.error.getMessage());
        this.error = null;

        // valid close
        listener.onClosed(this.socket, 1000, "goodbye");
        assertEquals(null, this.transcript);
        assertEquals(null, this.error);

        // mismatched request id
        message =
            "X-RequestId:42\r\n" +
            "Path:speech.phrase\r\n" +
            "\r\n" +
            "{\"RecognitionStatus\": \"Success\", \"DisplayText\": \"test\"}";
        listener.onMessage(this.socket, message);
        assertEquals(null, this.transcript);
        assertEquals(null, this.error);

        // mismatched path
        message =
            String.format("X-RequestId:%s\r\n", client.getRequestId()) +
            "Path:speech.hypothesis\r\n" +
            "\r\n" +
            "{\"RecognitionStatus\": \"Success\", \"DisplayText\": \"test\"}";
        listener.onMessage(this.socket, message);
        assertEquals(null, this.transcript);
        assertEquals(null, this.error);

        // mismatched status
        message =
            String.format("X-RequestId:%s\r\n", client.getRequestId()) +
            "Path:speech.phrase\r\n" +
            "\r\n" +
            "{\"RecognitionStatus\": \"EndOfDictation\", " +
            "\"DisplayText\": \"test\"}";
        listener.onMessage(this.socket, message);
        assertEquals(null, this.transcript);
        assertEquals(null, this.error);

        // no recognition
        message =
            String.format("X-RequestId:%s\r\n", client.getRequestId()) +
            "Path:speech.phrase\r\n" +
            "\r\n" +
            "{\"RecognitionStatus\": \"BabbleTimeout\", " +
            "\"DisplayText\": \"test\"}";
        listener.onMessage(this.socket, message);
        assertEquals("", this.transcript);
        assertEquals(null, this.error);

        // valid recognition
        message =
            String.format("X-RequestId:%s\r\n", client.getRequestId()) +
            "Path:speech.phrase\r\n" +
            "\r\n" +
            "{\"RecognitionStatus\": \"Success\", " +
            "\"DisplayText\": \"test\"}";
        listener.onMessage(this.socket, message);
        assertEquals("test", this.transcript);
        assertEquals(null, this.error);
    }

    public void onSpeech(String transcript) {
        this.transcript = transcript;
    }

    public void onError(Throwable e) {
        this.error = e;
    }
}
