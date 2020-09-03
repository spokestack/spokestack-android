package io.spokestack.spokestack.dialogue;

/**
 * This class represents an event dispatched by Spokestack's dialogue system.
 *
 * <p>
 * Dialogue events come in three types, each signifying which part of the {@link
 * ConversationState} in the event may have changed:
 * </p>
 *
 * <ol>
 *     <li>
 *         <b>state change</b>
 *         <p>
 *         This event indicates a likely update to the data being displayed to
 *         the user, often signalling a scene transition. A {@code prompt}
 *         event will often be dispatched along with state change events.
 *         </p>
 *     </li>
 *     <li>
 *         <b>action</b>
 *         <p>
 *         This event indicates that the user has requested the app to
 *         perform some action. It may be accompanied by a {@code prompt}
 *         event containing a system prompt to be read while the action is
 *         occurring, but such prompts are not guaranteed to exist.
 *         </p>
 *         <p>
 *         When the action (including any network requests for data, storing
 *         retrieved data in the conversation data store, etc.) is
 *         complete, {@code completeTurn()} should be called on the dialogue
 *         manager referenced by the event. This will allow any returned data
 *         to be validated against dialogue rules to determine the proper
 *         system reply, at which point a {@code navigation} and/or
 *         {@code prompt} event will be dispatched.
 *         </p>
 *     </li>
 *     <li>
 *         <b>prompt</b>
 *         <p>
 *         This event indicates that a system prompt should be displayed and/or
 *         read to the user.
 *         </p>
 *     </li>
 *     <li>
 *         <b>error</b>
 *         <p>
 *         This event indicates an error during dialogue turn processing.
 *         </p>
 *     </li>
 * </ol>
 */
public final class DialogueEvent {

    /**
     * The type of event being dispatched.
     */
    public enum Type {

        /**
         * The user has requested the app to perform an action.
         */
        ACTION,

        /**
         * A system prompt is ready to be displayed and/or read to the user.
         */
        PROMPT,

        /**
         * The user's conversational context has changed, which might indicate
         * the need for a visual transition in the app.
         */
        STATE_CHANGE,

        /**
         * A dialogue-related error has occurred.
         */
        ERROR
    }

    /**
     * The event's type.
     */
    public final Type type;

    /**
     * The state of the conversation at the time of the event.
     *
     * <p>
     * Note that {@link Type#ACTION ACTION} events may be dispatched with
     * prospective destination nodes that might not be valid until the action in
     * question is complete.
     * </p>
     */
    public final ConversationState state;

    /**
     * Create a new dialogue event.
     *
     * @param eventType The event's type.
     * @param newState  The state of the conversation at the time of the event.
     */
    public DialogueEvent(Type eventType, ConversationState newState) {
        this.type = eventType;
        this.state = newState;
    }

    @Override
    public String toString() {
        return "DialogueEvent{"
              + "type=" + type
              + ", state=" + state
              + '}';
    }
}
