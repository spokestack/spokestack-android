package com.pylon.spokestack;

import java.util.*;
import java.nio.ByteBuffer;

import org.junit.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.pylon.spokestack.SpeechContext;

public class SpeechContextTest implements OnSpeechEventListener {
    private SpeechContext.Event event;
    private SpeechContext context;

    @Test
    public void testBuffer() {
        SpeechContext context = new SpeechContext();
        assertEquals(null, context.getBuffer());

        Deque<ByteBuffer> buffer = new LinkedList<>();
        context.attachBuffer(buffer);
        assertEquals(buffer, context.getBuffer());

        context.detachBuffer();
        assertEquals(null, context.getBuffer());
    }

    @Test
    public void testIsActive() {
        SpeechContext context = new SpeechContext();
        assertFalse(context.isActive());

        context.setActive(true);
        assertTrue(context.isActive());

        context.setActive(false);
        assertFalse(context.isActive());
    }

    @Test
    public void testTranscript() {
        SpeechContext context = new SpeechContext();
        assertEquals(context.getTranscript(), "");

        context.setTranscript("test");
        assertEquals("test", context.getTranscript());
    }

    @Test
    public void testConfidence() {
        SpeechContext context = new SpeechContext();
        assertEquals(context.getConfidence(), 0.0);

        context.setConfidence(1.0);
        assertEquals(context.getConfidence(), 1.0);
    }

    @Test
    public void testError() {
        SpeechContext context = new SpeechContext();
        assertEquals(null, context.getError());

        context.setError(new Exception("test"));
        assertEquals("test", context.getError().getMessage());

        context.setError(null);
        assertEquals(null, context.getError());
    }

    @Test
    public void testReset() {
        SpeechContext context = new SpeechContext();
        context.setActive(true);
        context.setTranscript("test");
        context.setConfidence(1.0);
        context.setError(new Exception("test"));

        context.reset();
        assertFalse(context.isActive());
        assertEquals(context.getTranscript(), "");
        assertEquals(context.getConfidence(), 0.0);
        assertEquals(null, context.getError());
    }

    @Test
    public void testDispatch() {
        SpeechContext context = new SpeechContext();
        context.addOnSpeechEventListener(this);
        context.dispatch(SpeechContext.Event.ACTIVATE);
        assertEquals(SpeechContext.Event.ACTIVATE, this.event);
        assertEquals(context, this.context);

        context.removeOnSpeechEventListener(this);
        this.event = null;
        this.context = null;
        context.dispatch(SpeechContext.Event.ACTIVATE);
        assertEquals(null, this.event);
        assertEquals(null, this.context);
    }

    @Test
    public void testSpeechEvents() {
        assertEquals("activate", SpeechContext.Event.ACTIVATE.toString());
        assertEquals("deactivate", SpeechContext.Event.DEACTIVATE.toString());
        assertEquals("recognize", SpeechContext.Event.RECOGNIZE.toString());
        assertEquals("error", SpeechContext.Event.ERROR.toString());
    }

    public void onEvent(SpeechContext.Event event, SpeechContext context) {
        this.event = event;
        this.context = context;
    }
}
