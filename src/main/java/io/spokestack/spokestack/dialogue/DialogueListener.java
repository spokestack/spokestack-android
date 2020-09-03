package io.spokestack.spokestack.dialogue;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.util.TraceListener;

/**
 * Interface for classes that wish to receive dialogue events and/or log
 * messages from the dialogue subsystem.
 */
public interface DialogueListener extends TraceListener {

    /**
     * A notification that a dialogue event has occurred.
     *
     * @param event The dialogue event.
     */
    void onDialogueEvent(@NonNull DialogueEvent event);
}
