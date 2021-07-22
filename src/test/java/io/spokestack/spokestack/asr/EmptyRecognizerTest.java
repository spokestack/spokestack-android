package io.spokestack.spokestack.asr;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;
import org.junit.Test;

import static org.junit.Assert.*;

public class EmptyRecognizerTest {

    @Test
    public void testProcess() throws Exception {
        SpeechConfig config = new SpeechConfig();
        EmptyRecognizer recognizer = new EmptyRecognizer(config);
        assertFalse(recognizer.isActive());
        SpeechContext context = new SpeechContext(config);
        // context is inactive, so the stage does nothing
        recognizer.process(context, null);
        assertFalse(recognizer.isActive());
        // the first process call after activation sets the internal flag
        // but doesn't deactivate the context
        context.setActive(true);
        recognizer.process(context, null);
        assertTrue(recognizer.isActive());
        assertTrue(context.isActive());
        // another process call deactivates both the context and
        // the internal flag
        recognizer.process(context, null);
        assertFalse(context.isActive());
        assertFalse(recognizer.isActive());
    }
}
