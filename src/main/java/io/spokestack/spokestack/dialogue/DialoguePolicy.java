package io.spokestack.spokestack.dialogue;

import io.spokestack.spokestack.nlu.NLUResult;

/**
 * <p>
 * The API for dialogue policies used by Spokestack's {@link
 * io.spokestack.spokestack.dialogue.DialogueManager DialogueManager}
 * component.
 * </p>
 *
 * <p>
 * A dialogue policy must have a no-argument constructor to be used by the
 * dialogue management system.
 * </p>
 */
public interface DialoguePolicy {

    /**
     * Store the internal state of the dialogue policy in the specified data
     * store for cross-session persistence.
     *
     * @param conversationData The data store where policy state should be
     *                         saved.
     */
    void dump(ConversationData conversationData);

    /**
     * Load previously stored internal state from the specified data store.
     *
     * @param conversationData The data store containing stored policy state.
     */
    void load(ConversationData conversationData);

    /**
     * Process a user turn and return a relevant response.
     *
     * @param userTurn         The user input as determined by the NLU
     *                         component.
     * @param conversationData Conversation data used to resolve and prepare a
     *                         response.
     * @param eventDispatcher  Dispatcher used to notify listeners of dialogue
     *                         events.
     */
    void handleTurn(NLUResult userTurn,
                    ConversationData conversationData,
                    DialogueDispatcher eventDispatcher);

    /**
     * Complete the pending user turn.
     * <p>
     * This method should be called after any actions or data retrieval pending
     * in the app are completed.
     * </p>
     *
     * @param conversationData Conversation data used to resolve and prepare a
     *                         response.
     * @param eventDispatcher  Dispatcher used to notify listeners of dialogue
     *                         events.
     */
    void completeTurn(ConversationData conversationData,
                      DialogueDispatcher eventDispatcher);
}
