package io.spokestack.spokestack.tts;

import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

/**
 * An interface used to receive playback URLs asynchronously from the
 * Spokestack TTS service.
 */
public abstract class TTSCallback implements Callback {
    private final Gson gson = new Gson();

    @Override
    public void onFailure(Call call, IOException e) {
        onError("Unknown synthesis error: " + e.getMessage());
    }

    @Override
    public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
            if (response.code() == 403) {
                onError("Invalid API key");
            }
            onError("Synthesis error: HTTP " + response.code());
        } else {
            String responseJson = response.body().string();
            Map result = gson.fromJson(responseJson, Map.class);
            String audioUrl = (String) result.get("url");
            onUrlReceived(audioUrl);
        }
    }

    /**
     * Communicates an error during synthesis.
     *
     * @param message The error message to be delivered.
     */
    public abstract void onError(String message);

    /**
     * <p>
     * Delivers an audio URL where synthesized speech can be streamed.
     * </p>
     *
     * <p>
     * Spokestack audio URLS must be accessed within 30 seconds; after that,
     * the stream will not be playable.
     * </p>
     *
     * @param url A URL from which synthesized audio can be streamed.
     */
    public abstract void onUrlReceived(String url);
}
