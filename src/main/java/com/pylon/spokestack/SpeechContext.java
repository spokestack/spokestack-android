package com.pylon.spokestack;

import java.util.Deque;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

/**
 * SpokeStack speech recognition context
 *
 * <p>
 * This class maintains global state for the speech pipeline, allowing
 * pipeline components to communicate information among themselves and
 * event handlers.
 * </p>
 */
public final class SpeechContext {
    /** speech event types. */
    public enum Event {
        /** speech recognition has become active. */
        ACTIVATE("activate"),
        /** speech recognition has become inactive. */
        DEACTIVATE("deactivate"),
        /** speech was recognized. */
        RECOGNIZE("recognize"),
        /** a speech error occurred. */
        ERROR("error");

        private final String event;

        Event(String e) {
            this.event = e;
        }

        /** @return the event type string */
        @Override
        public String toString() {
            return this.event;
        }
    }

    private final List<OnSpeechEventListener> listeners = new ArrayList<>();
    private Deque<ByteBuffer> buffer;
    private boolean active;
    private String transcript = "";
    private double confidence;
    private Throwable error;

    /** @return speech frame buffer */
    public Deque<ByteBuffer> getBuffer() {
        return this.buffer;
    }

    /**
     * attaches a frame buffer to the context.
     * @param value frame buffer to attach
     */
    public void attachBuffer(Deque<ByteBuffer> value) {
        this.buffer = value;
    }

    /**
     * removes the attached frame buffer.
     */
    public void detachBuffer() {
        this.buffer = null;
    }

    /** @return speech recognition active indicator */
    public boolean isActive() {
        return this.active;
    }

    /**
     * activates speech recognition.
     * @param value value to assign
     */
    public void setActive(boolean value) {
        this.active = value;
    }

    /** @return the current speech transcript. */
    public String getTranscript() {
        return this.transcript;
    }

    /**
     * updates the current speech transcript.
     * @param value speech text value to assign
     */
    public void setTranscript(String value) {
        this.transcript = value;
    }

    /** @return the current speech recognition confidence: [0-1) */
    public double getConfidence() {
        return this.confidence;
    }

    /**
     * updates the current speech confidence level.
     * @param value speech confidence to assign
     */
    public void setConfidence(double value) {
        this.confidence = value;
    }

    /** @return the last error raised on the context */
    public Throwable getError() {
        return this.error;
    }

    /**
     * raises an error with the speech context.
     * @param value the exception to attach
     */
    public void setError(Throwable value) {
        this.error = value;
    }

    /**
     * resets the context to the default state.
     */
    public void reset() {
        setActive(false);
        setTranscript("");
        setConfidence(0);
        setError(null);
    }

    /**
     * dispatches a speech event.
     * @param event the event to publish
     */
    public void dispatch(Event event) {
        for (OnSpeechEventListener listener: this.listeners)
            listener.onEvent(event, this);
    }

    /**
     * attaches a speech listener.
     * @param listener listener callback to attach
     */
    public void addOnSpeechEventListener(OnSpeechEventListener listener) {
        this.listeners.add(listener);
    }

    /**
     * detaches a speech listener.
     * @param listener listener callback to remove
     */
    public void removeOnSpeechEventListener(OnSpeechEventListener listener) {
        this.listeners.remove(listener);
    }
}
