package io.spokestack.spokestack.tts;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;

/**
 * An interface used to receive playback URLs asynchronously from the Spokestack
 * TTS service.
 */
public abstract class TTSCallback implements Callback {
    private final Gson gson = new Gson();

    @Override
    public void onFailure(@NonNull Call call, IOException e) {
        onError("Unknown synthesis error: " + e.getMessage());
    }

    @Override
    public void onResponse(@NonNull Call call, Response httpResponse)
          throws IOException {
        if (!httpResponse.isSuccessful()) {
            if (httpResponse.code() == 403) {
                onError("Invalid API key");
            } else {
                onError("Synthesis error: HTTP " + httpResponse.code());
            }
        } else {
            String responseJson = httpResponse.body().string();
            String requestId = httpResponse.header("x-request-id");

            AudioResponse audioResponse =
                  createAudioResponse(responseJson, requestId);
            onSynthesisResponse(audioResponse);
        }
    }

    /**
     * Use data in the HTTP response to create an {@link AudioResponse}.
     *
     * @param responseJson The response body as a JSON string.
     * @param requestId The request ID from the response header.
     *                  May be {@code null}.
     * @return An {@link AudioResponse} containing the URI where synthesized
     * audio is available and any additional metadata.
     */
    protected AudioResponse createAudioResponse(@NonNull String responseJson,
                                                @Nullable String requestId) {
        SpokestackSynthesisResponse result =
              gson.fromJson(responseJson,
                    SpokestackSynthesisResponse.class);
        Uri audioUri = Uri.parse(result.getUrl());

        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("id", requestId);
        return new AudioResponse(metadata, audioUri);
    }

    /**
     * Communicates an error during synthesis.
     *
     * @param message The error message to be delivered.
     */
    public abstract void onError(String message);

    /**
     * <p>
     * Delivers an audio response, including the URL where synthesized speech
     * can be streamed.
     * </p>
     *
     * <p>
     * Spokestack audio URLS must be accessed within 30 seconds; after that, the
     * stream will not be playable.
     * </p>
     *
     * @param response An AudioResponse containing the URL from which
     *                 synthesized audio can be streamed and other relevant
     *                 metadata, including the request ID if one was supplied.
     */
    public abstract void onSynthesisResponse(AudioResponse response);
}
