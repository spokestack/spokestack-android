package io.spokestack.spokestack.asr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.spokestack.spokestack.util.Crypto;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Spokestack cloud speech client.
 *
 * <p>
 * This client encapsulates the websocket logic used to communicate with
 * Spokestack's cloud-based ASR service.
 * </p>
 */
public class SpokestackCloudClient {
    private static final int MESSAGE_BUFFER_SIZE = 1280;
    private static final int N_BEST = 1;
    private static final String SOCKET_URL =
          "wss://api.spokestack.io/v1/asr/websocket";

    private final Listener listener;
    private final OkHttpClient client;
    private final String authMessage;
    private final ByteBuffer buffer;
    private WebSocket socket;

    SpokestackCloudClient(Builder builder) {
        this.listener = builder.listener;
        this.buffer = ByteBuffer.allocateDirect(MESSAGE_BUFFER_SIZE);
        this.authMessage = authMessage(builder);
        this.client = builder.getHttpClient();
    }

    private String authMessage(Builder builder) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        Map<String, Object> body = new HashMap<>();
        body.put("format", "PCM16LE");
        body.put("rate", builder.sampleRate);
        body.put("language", builder.lang);
        body.put("limit", N_BEST);
        String bodyJson = gson.toJson(body);
        String signature = Crypto.signBody(bodyJson, builder.apiSecret);

        body.clear();
        body.put("keyId", builder.apiId);
        body.put("signature", signature);
        body.put("body", bodyJson);
        return gson.toJson(body);
    }

    /**
     * releases the resources associated with the speech client.
     */
    public void close() {
        disconnect();
    }

    /**
     * @return true if the websocket is currently connected, false otherwise
     */
    public boolean isConnected() {
        return this.socket != null;
    }

    /**
     * establishes a websocket connection for speech recognition.
     */
    public void connect() {
        if (this.socket != null) {
            throw new IllegalStateException();
        }

        Request request = new Request.Builder().url(SOCKET_URL).build();
        this.socket = this.client.newWebSocket(request, new SocketListener());
        this.socket.send(this.authMessage);
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
                this.buffer.clear();
            }
        }
    }

    /**
     * transmits an audio frame over the websocket.
     *
     * @param frame the audio frame buffer to send
     */
    public void sendAudio(ByteBuffer frame) {
        if (this.socket == null) {
            throw new IllegalStateException();
        }

        if (this.buffer.remaining() < frame.capacity()) {
            flush();
        }

        frame.rewind();
        this.buffer.put(frame);
    }

    /**
     * sends any buffered data followed by an empty message indicating the end
     * of the utterance.
     */
    public void endAudio() {
        if (this.socket == null) {
            throw new IllegalStateException();
        }

        flush();
        this.socket.send(ByteString.EMPTY);
    }

    private void flush() {
        if (this.buffer.position() > 0) {
            this.buffer.flip();
            this.socket.send(ByteString.of(this.buffer));
            this.buffer.clear();
        }
    }

    /**
     * okhttp socket callback.
     */
    private class SocketListener extends WebSocketListener {
        private final Gson gson = new Gson();
        private final Listener listener = SpokestackCloudClient.this.listener;

        @Override
        public void onMessage(@NotNull WebSocket s, @NotNull String message) {
            SpokestackASRResponse response =
                  gson.fromJson(message, SpokestackASRResponse.class);
            if (response.error != null) {
                String err = String.format("ASR error: %s", response.error);
                this.listener.onError(new Exception(err));
            } else if (response.status.equals("ok")
                  && response.hypotheses.length > 0) {
                String speech = response.hypotheses[0].transcript;
                float confidence = response.hypotheses[0].confidence;
                this.listener.onSpeech(speech, confidence, response.isFinal);
            }
        }

        @Override
        public void onClosed(@NotNull WebSocket s,
                             int code,
                             @NotNull String reason) {
            if (code != 1000) {
                String err = String.format("close error %d: %s", code, reason);
                this.listener.onError(new Exception(err));
            }
        }

        @Override
        public void onFailure(@NotNull WebSocket s,
                              @NotNull Throwable e,
                              Response r) {
            this.listener.onError(e);
        }
    }

    /**
     * Spokestack speech client builder.
     */
    public static class Builder {
        private String apiId;
        private String apiSecret;
        private String lang;
        private int sampleRate;
        private Listener listener;

        /**
         * sets the Spokestack credentials used for authentication.
         *
         * @param clientId client ID
         * @param secret   secret key
         * @return this
         */
        public Builder setCredentials(String clientId, String secret) {
            this.apiId = clientId;
            this.apiSecret = secret;
            return this;
        }

        /**
         * sets the language code.
         *
         * @param value language code
         * @return this
         */
        public Builder setLang(String value) {
            this.lang = value;
            return this;
        }

        /**
         * sets the audio sample rate.
         *
         * @param value sample rate, in Hz
         * @return this
         */
        public Builder setSampleRate(int value) {
            this.sampleRate = value;
            return this;
        }

        /**
         * attaches the listener callback.
         *
         * @param value event listener to configure
         * @return this
         */
        public Builder setListener(Listener value) {
            this.listener = value;
            return this;
        }

        /**
         * initializes a new speech client instance.
         *
         * @return the constructed speech client
         * @throws IllegalArgumentException if the configuration is invalid
         */
        public SpokestackCloudClient build() {
            if (this.apiId == null || this.apiSecret == null) {
                throw new IllegalArgumentException("no credentials");
            }
            if (this.lang == null) {
                throw new IllegalArgumentException("no language");
            }
            if (this.sampleRate == 0) {
                throw new IllegalArgumentException("no sampleRate");
            }
            if (this.listener == null) {
                throw new IllegalArgumentException("no listener");
            }
            return new SpokestackCloudClient(this);
        }

        /**
         * get a new HTTP client. used for testing.
         *
         * @return a new HTTP client
         */
        public OkHttpClient getHttpClient() {
            return new OkHttpClient.Builder().build();
        }
    }

    /**
     * speech client listener callback interface.
     */
    public interface Listener {
        /**
         * called when a speech transcription is received.
         *  @param transcript the speech transcript
         * @param confidence the recognizer's confidence in {@code transcript}
         * @param isFinal    flag representing whether the result is final
         */
        void onSpeech(String transcript, float confidence, boolean isFinal);

        /**
         * called when a speech detection error occurred.
         *
         * @param e the speech error
         */
        void onError(Throwable e);
    }
}
