package com.pylon.spokestack.microsoft;

import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;

import com.google.gson.Gson;

import okio.ByteString;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * microsoft bing speech api client.
 *
 * <p>
 * This class uses the Microsoft speech recognition service for automatic
 * speech recognition. It implements the client side of the websocket protocol
 * defined <a href="https://goo.gl/qftX5j">here</a>. Transcripts and errors
 * are delivered to the client asynchronously via the {@link Listener}
 * interface. The following sample demonstrates how to configure and use the
 * client.
 * </p>
 *
 * <pre>
 * {@code
 *  BingSpeechClient client = new BingSpeechClient.Builder()
 *      .setApiKey("<my-api-key>")
 *      .setLocale("en-US")
 *      .setSampleRate(16000)
 *      .setListener(this)
 *      .build();
 *
 *  client.connect();
 *
 *  client.beginAudio();
 *  for (ByteBuffer frame: frames)
 *      client.sendAudio(frame);
 *  client.endAudio();
 *
 *  client.disconnect();
 * }
 * </pre>
 */
public final class BingSpeechClient implements AutoCloseable {
    private static final int MESSAGE_BUFFER_SIZE = 8192;
    private static final String TOKEN_URL =
        "https://api.cognitive.microsoft.com/sts/v1.0/issueToken";
    private static final String TOKEN_HEADER_APIKEY =
        "Ocp-Apim-Subscription-Key";
    private static final String SOCKET_URL =
        "wss://speech.platform.bing.com"
        + "/speech/recognition/conversation/cognitiveservices/v1?language=%s";

    private final int sampleRate;
    private final String locale;
    private final Listener listener;
    private final OkHttpClient client;
    private final String token;
    private final ByteBuffer buffer;
    private WebSocket socket;
    private String requestId;

    private BingSpeechClient(Builder builder) throws Exception {
        this.sampleRate = builder.sampleRate;
        this.locale = builder.locale;
        this.listener = builder.listener;
        this.buffer = ByteBuffer.allocateDirect(MESSAGE_BUFFER_SIZE);
        this.client = builder.getHttpClient();

        Request tokenRequest = new Request.Builder()
            .url(TOKEN_URL)
            .addHeader(TOKEN_HEADER_APIKEY, builder.apiKey)
            .post(RequestBody.create(null, new byte[0]))
            .build();
        Response tokenResponse = this.client
            .newCall(tokenRequest)
            .execute();
        if (tokenResponse.code() != 200)
            throw new Exception("authentication failed");
        this.token = tokenResponse.body().string();
    }

    /**
     * releases the resources associated with the speech client.
     */
    public void close() {
        disconnect();
    }

    /**
     * @return true if the websocket is currently connect, false otherwise
     */
    public boolean isConnected() {
        return this.socket != null;
    }

    /**
     * @return the request identifier of the current turn
     */
    public String getRequestId() {
        return this.requestId;
    }

    /**
     * establishes a websocket connection for speech recognition.
     */
    public void connect() {
        if (this.socket != null)
            throw new IllegalStateException();

        String connectionId = UUID.randomUUID().toString().replace("-", "");
        Request request = new Request.Builder()
            .url(String.format(SOCKET_URL, this.locale))
            .addHeader("Authorization", String.format("Bearer %s", this.token))
            .addHeader("X-ConnectionId", connectionId)
            .build();
        this.socket = this.client.newWebSocket(request, new SocketListener());
    }

    /**
     * disconnects the websocket.
     */
    public void disconnect() {
        if (this.socket != null) {
            try {
                this.socket.close(1000, "goodbye");
            } finally {
                this.socket = null;
                this.requestId = null;
                this.buffer.clear();
            }
        }
    }

    /**
     * begins audio transmission for a new conversation turn.
     * @throws Exception on error
     */
    public void beginAudio() throws Exception {
        if (this.socket == null)
            throw new IllegalStateException();

        this.requestId = UUID.randomUUID().toString().replace("-", "");
        sendConfig();
        sendHeader();
    }

    private void sendConfig() throws Exception {
        String header =
            "Path:speech.config\r\n"
            + String.format("X-RequestId:%s\r\n", this.requestId)
            + String.format("X-Timestamp:%s\r\n", now())
            + "Content-Type:application/json; charset=utf-8\r\n";
        String body = "{}";
        String message = String.format("%s\r\n%s", header, body);
        this.socket.send(message);
    }

    private void sendHeader() throws Exception {
        ByteBuffer message = ByteBuffer.allocateDirect(44)
            .order(ByteOrder.LITTLE_ENDIAN)         // wave endian
            .put("RIFF".getBytes("US-ASCII"))       // riff chunk id
            .putInt(0)                              // file size
            .put("WAVE".getBytes("US-ASCII"))       // riff format
            .put("fmt ".getBytes("US-ASCII"))       // begin format chunk
            .putInt(16)                             // format chunk size
            .putShort((short) 1)                    // format (1=PCM)
            .putShort((short) 1)                    // channel count
            .putInt(this.sampleRate)                // sample rate
            .putInt(this.sampleRate * 2)            // byte rate
            .putShort((short) 2)                    // block alignment
            .putShort((short) 16)                   // bits per sample
            .put("data".getBytes("US-ASCII"))       // begin data chunk
            .putInt(0);                             // data chunk size
        sendAudio(message);
        flush();
    }

    /**
     * transmits an audio frame over the websocket.
     * @param frame the audio frame buffer to send
     * @throws Exception on error
     */
    public void sendAudio(ByteBuffer frame) throws Exception {
        if (this.socket == null)
            throw new IllegalStateException();

        if (frame == null || this.buffer.remaining() < frame.capacity())
            flush();

        if (this.buffer.position() == 0) {
            byte[] header = (
                "Path:audio\r\n"
                + String.format("X-RequestId:%s\r\n", this.requestId)
                + String.format("X-Timestamp:%s\r\n", now())
                + "Content-Type:audio/x-wav\r\n"
            ).getBytes("US-ASCII");

            this.buffer
                .order(ByteOrder.BIG_ENDIAN)        // protocol endian
                .putShort((short) header.length)    // message header length
                .put(header);                       // message header
        }

        if (frame != null) {
            frame.rewind();
            this.buffer.put(frame);                 // message body
        }
    }

    /**
     * sends an empty audio frame indicating the end of the turn.
     * @throws Exception on error
     */
    public void endAudio() throws Exception {
        if (this.socket == null)
            throw new IllegalStateException();

        sendAudio(null);
        flush();
    }

    private void flush() {
        if (this.buffer.position() > 0) {
            this.buffer.flip();
            this.socket.send(ByteString.of(this.buffer));
            this.buffer.clear();
        }
    }

    private String now() {
        SimpleDateFormat formatter =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'000Z'");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(new Date());
    }

    /** okhttp socket callback. */
    private class SocketListener extends WebSocketListener {
        private final Gson gson = new Gson();
        private final Listener listener = BingSpeechClient.this.listener;

        @Override
        public void onMessage(WebSocket s, String message) {
            String requestHeader = String.format(
                "X-RequestId:%s",
                BingSpeechClient.this.requestId
            );
            if (message.contains(requestHeader))
                if (message.contains("Path:speech.phrase"))
                    onSpeechPhrase(message);
        }

        @Override
        public void onClosed(WebSocket s, int code, String reason) {
            if (code != 1000)
                this.listener.onError(
                    new Exception(String.format("%d: %s", code, reason))
                );
        }

        @Override
        public void onFailure(WebSocket s, Throwable e, Response r) {
            this.listener.onError(e);
        }

        private void onSpeechPhrase(String message) {
            String body = message.split("\r\n\r\n", 2)[1];
            Map result = gson.fromJson(body, Map.class);
            String status = (String) result.get("RecognitionStatus");
            if (status.equals("Success"))
                this.listener.onSpeech((String) result.get("DisplayText"));
            else if (!status.equals("EndOfDictation"))
                this.listener.onSpeech("");
        }
    }

    /**
     * bing speech client builder.
     */
    public static class Builder {
        private String apiKey;
        private String locale;
        private int sampleRate;
        private Listener listener;

        /**
         * sets the microsoft azure api key for authentication.
         * @param value api key to configure
         * @return this
         */
        public Builder setApiKey(String value) {
            this.apiKey = value;
            return this;
        }

        /**
         * sets the country/language code string.
         * @param value locale code to configure
         * @return this
         */
        public Builder setLocale(String value) {
            this.locale = value;
            return this;
        }

        /**
         * sets the audio sample rate.
         * @param value sample rate to configure, in Hz
         * @return this
         */
        public Builder setSampleRate(int value) {
            this.sampleRate = value;
            return this;
        }

        /**
         * attaches the listener callback.
         * @param value event listener to configure
         * @return this
         */
        public Builder setListener(Listener value) {
            this.listener = value;
            return this;
        }

        /**
         * initializes a new speech client instance.
         * @return the constructed speech client
         * @throws Exception on error
         */
        public BingSpeechClient build() throws Exception {
            if (this.apiKey == null)
                throw new IllegalArgumentException("apiKey");
            if (this.locale == null)
                throw new IllegalArgumentException("locale");
            if (this.sampleRate == 0)
                throw new IllegalArgumentException("sampleRate");
            if (this.listener == null)
                throw new IllegalArgumentException("listener");
            return new BingSpeechClient(this);
        }

        OkHttpClient getHttpClient() {
            return new OkHttpClient.Builder().build();
        }
    }

    /**
     * speech client listener callback interface.
     */
    public interface Listener {
        /**
         * called when a speech transcription is received.
         * @param transcript the speech transcript, or "" if no speech
         *                   was detected
         */
        void onSpeech(String transcript);

        /**
         * called when a speech detection error occurred.
         * @param e the speech error
         */
        void onError(Throwable e);
    }
}
