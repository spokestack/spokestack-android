package io.spokestack.spokestack.dialogue;

import io.spokestack.spokestack.util.EventTracer;

import java.util.List;

/**
 * A component that dispatches dialogue events to registered listeners.
 */
public final class DialogueDispatcher extends EventTracer {

    private final List<DialogueListener> listeners;

    /**
     * Create a new event dispatcher.
     *
     * @param level          The threshold that events must surpass to be
     *                       delivered.
     * @param eventListeners Listeners that should receive dialogue and trace
     *                       events.
     */
    public DialogueDispatcher(int level,
                              List<DialogueListener> eventListeners) {
        super(level, eventListeners);
        this.listeners = eventListeners;
    }

    /**
     * Send a dialogue event to all registered listeners.
     *
     * @param event The event to dispatch.
     */
    public void dispatch(DialogueEvent event) {
        for (DialogueListener listener : listeners) {
            listener.onDialogueEvent(event);
        }
    }

    /**
     * Add a new listener to receive events from the dispatcher.
     *
     * @param listener The listener to add.
     */
    public void addListener(DialogueListener listener) {
        this.listeners.add(listener);
    }

    /**
     * Remove a dialogue listener, allowing it to be garbage collected.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(DialogueListener listener) {
        this.listeners.remove(listener);
    }
}
