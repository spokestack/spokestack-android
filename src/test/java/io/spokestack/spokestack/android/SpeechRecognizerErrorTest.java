package io.spokestack.spokestack.android;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpeechRecognizerErrorTest {

    @Test
    public void testErrorCodes() {
        SpeechRecognizerError error = new SpeechRecognizerError(0);
        assertTrue(error.getMessage().contains(
              SpeechRecognizerError.Description.UNKNOWN_ERROR.toString()));
        error = new SpeechRecognizerError(1);
        assertTrue(error.getMessage().contains(
              SpeechRecognizerError.Description.NETWORK_TIMEOUT.toString()));
        error = new SpeechRecognizerError(13);
        assertTrue(error.getMessage().contains(
              SpeechRecognizerError.Description.UNKNOWN_ERROR.toString()));
    }
}