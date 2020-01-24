package io.spokestack.spokestack.tts;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpokestackSynthesisResponseTest {
    private Gson gson = new Gson();

    private static final String[] KNOWN_METHODS = {"Markdown", "Ssml", "Text"};

    @Test
    public void testDeserialization() {
        String url = "https://spokestack.io/speech.mp3";

        // invalid request
        String responseJson = "{\"data\": null }";
        SpokestackSynthesisResponse result =
              gson.fromJson(responseJson,
                    SpokestackSynthesisResponse.class);
        assertNull(result.getUrl());

        // unknown method
        // this would indicate a method supported by the synthesis API and
        // added to the client but not added to SpokestackSynthesisResponse
        responseJson =
              "{\"data\": {\"synthesize\": {\"url\": \"" + url + "\"}}}";
        result = gson.fromJson(responseJson, SpokestackSynthesisResponse.class);
        assertNull(result.getUrl());

        for (String method : KNOWN_METHODS) {
            responseJson = String.format(
                  "{\"data\": {\"synthesize%s\": {\"url\": \"" + url + "\"}}}",
                  method
            );
            result = gson.fromJson(
                  responseJson, SpokestackSynthesisResponse.class);
            assertEquals(url, result.getUrl());

        }
    }
}