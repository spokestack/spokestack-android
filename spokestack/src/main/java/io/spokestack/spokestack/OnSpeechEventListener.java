package io.spokestack.spokestack;

/**
 * speech event callback interface.
 *
 * This is the primary event interface in Spokestack. The speech pipeline
 * routes events/errors asynchronously through it as they occur.
 */
public interface OnSpeechEventListener {
    /**
     * receives a speech event.
     * @param event   the name of the event that was raised
     * @param context the current speech context
     * @throws Exception on error
     */
    void onEvent(SpeechContext.Event event, SpeechContext context)
        throws Exception;
}
