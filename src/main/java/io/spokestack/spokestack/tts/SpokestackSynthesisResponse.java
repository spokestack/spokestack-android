package io.spokestack.spokestack.tts;

/**
 * A simple wrapper class used for parsing GraphQL responses from the Spokestack
 * TTS service.
 */
public class SpokestackSynthesisResponse {

    private ResponseData data;

    /**
     * @return The URL where synthesized audio can be streamed.
     */
    public String getUrl() {
        if (data == null) {
            return null;
        }

        ResponseMethod method = data.synthesizeMarkdown;
        if (method == null) {
            method = data.synthesizeSsml;
        }
        if (method == null) {
            method = data.synthesizeText;
        }

        if (method == null) {
            return null;
        }
        return method.url;
    }

    /**
     * Wrapper class used for deserializing synthesis responses.
     */
    private static class ResponseData {
        private ResponseMethod synthesizeMarkdown;
        private ResponseMethod synthesizeSsml;
        private ResponseMethod synthesizeText;
    }

    /**
     * Wrapper class used for deserializing synthesis responses.
     */
    private static class ResponseMethod {
        private String url;
    }
}
