package io.spokestack.spokestack.tts;

import java.util.ArrayList;
import java.util.List;

/**
 * The basic interface required of all TTS components. To participate in the TTS
 * subsystem, components must publish their events to any subscribed {@link
 * TTSListener}s.
 */
public abstract class TTSComponent {

    /**
     * Listeners that receive TTS events dispatched by this component.
     */
    private List<TTSListener> listeners = new ArrayList<>();

    /**
     * Add a TTS listener to receive events from this component.
     *
     * @param listener The listener to add.
     */
    public void addListener(TTSListener listener) {
        listeners.add(listener);
    }

    /**
     * Notify all subscribed TTS listeners that an event has occurred.
     *
     * @param event The event that has occurred.
     */
    public void dispatch(TTSEvent event) {
        for (TTSListener listener : listeners) {
            listener.eventReceived(event);
        }
    }
}
