package com.pylon.spokestack;

/**
 * speech event callback interface.
 *
 * This is the primary event interface in SpokeStack. The speech pipeline
 * routes events/errors asynchronously through it as they occur.
 */
public interface OnSpeechEventListener {
    /**
     * receives a speech event.
     * @param event   the name of the event that was raised
     * @param context the current speech context
     */
    void onEvent(SpeechContext.Event event, SpeechContext context);
}
