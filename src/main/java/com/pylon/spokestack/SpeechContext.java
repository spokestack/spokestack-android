package com.pylon.spokestack;

import java.util.List;
import java.util.ArrayList;

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
        RECOGNIZE("recognize");

        private final String event;

        Event(String e) {
            this.event = e;
        }

        /** @return the event type string */
        public String getValue() {
            return this.event;
        }
    }

    private final List<OnSpeechEventListener> listeners =
        new ArrayList<OnSpeechEventListener>();
    private boolean active;
    private String transcript = "";
    private double confidence;

    /** @return speech recognition active indicator */
    public boolean isActive() {
        return this.active;
    }

    /**
     * activates speech recognition.
     * @param activeValue value to assign
     */
    public void setActive(boolean activeValue) {
        this.active = activeValue;
    }

    /** @return the current speech transcript. */
    public String getTranscript() {
        return this.transcript;
    }

    /**
     * updates the current speech transcript.
     * @param transcriptValue speech text value to assign
     */
    public void setTranscript(String transcriptValue) {
        this.transcript = transcriptValue;
    }

    /** @return the current speech recognition confidence: [0-1) */
    public double getConfidence() {
        return this.confidence;
    }

    /**
     * updates the current speech confidence level.
     * @param confidenceValue speech confidence to assign
     */
    public void setConfidence(double confidenceValue) {
        this.confidence = confidenceValue;
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

    /** speech event interface. */
    public interface OnSpeechEventListener {
        /**
         * receives a speech event.
         * @param event   the name of the event that was raised
         * @param context the current speech context instance
         */
        void onEvent(Event event, SpeechContext context);
    }
}
