package io.spokestack.spokestack;

import androidx.annotation.NonNull;

/**
 * speech event callback interface.
 *
 * <p>
 * This is the primary event interface in Spokestack. The speech pipeline routes
 * events/errors asynchronously through it as they occur.
 * </p>
 */
public interface OnSpeechEventListener {
    /**
     * receives a speech event.
     *
     * @param event   the name of the event that was raised
     * @param context the current speech context
     * @throws Exception on error
     */
    void onEvent(@NonNull SpeechContext.Event event,
                 @NonNull SpeechContext context)
          throws Exception;
}
