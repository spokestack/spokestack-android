package io.spokestack.spokestack.tts;

/**
 * A simple wrapper class used for parsing GraphQL responses from the Spokestack
 * TTS service.
 */
public class SpokestackSynthesisResponse {

    private ResponseData data;
    private ResponseError[] errors;

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
     * @return A concatenation of all error messages from the response.
     */
    public String getError() {
        if (errors == null || errors.length == 0) {
            return null;
        }
        StringBuilder errorBuilder = new StringBuilder();
        for (int i = 0; i < errors.length; i++) {
            if (i > 0) {
                errorBuilder.append("; ");
            }
            errorBuilder.append(errors[i].message);
        }
        return errorBuilder.toString();
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

    /**
     * Wrapper class used for deserializing GraphQL errors.
     */
    private static class ResponseError {
        private String message;
    }
}
