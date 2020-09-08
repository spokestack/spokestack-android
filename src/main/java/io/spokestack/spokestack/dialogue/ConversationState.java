package io.spokestack.spokestack.dialogue;


import io.spokestack.spokestack.nlu.Slot;

import java.util.HashMap;
import java.util.Map;

/**
 * The current state of the conversation at the time of a dialogue event.
 */
public final class ConversationState {

    private final String frameName;
    private final String nodeName;
    private final String appAction;
    private final Prompt systemPrompt;
    private final String error;
    private final Map<String, Slot> slots;

    /**
     * Create a new conversation state.
     *
     * @param builder The current conversation state builder.
     */
    private ConversationState(Builder builder) {
        if (builder.conversationNode != null) {
            String[] parts = builder.conversationNode.split("\\.", 2);
            this.frameName = parts[0];
            this.nodeName = parts[1];
        } else {
            this.frameName = null;
            this.nodeName = null;
        }
        this.appAction = builder.action;
        this.systemPrompt = builder.systemPrompt;
        this.error = builder.error;
        this.slots = builder.slots;
    }

    /**
     * @return The name of the dialogue frame associated with this state.
     */
    public String getFrameName() {
        return frameName;
    }

    /**
     * @return The name of the dialogue node associated with this state.
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * @return The name of the app action or feature associated with this state.
     */
    public String getAction() {
        return appAction;
    }

    /**
     * @return The slot keys and values related to the current action, if any.
     */
    public Map<String, Slot> getSlots() {
        return slots;
    }

    /**
     * @return The prompt associated with this state.
     */
    public Prompt getPrompt() {
        return systemPrompt;
    }

    /**
     * @return The error associated with this state.
     */
    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "ConversationState{"
              + "frameName='" + frameName + '\''
              + ", nodeName='" + nodeName + '\''
              + ", appAction='" + appAction + '\''
              + ", slots='" + slots + '\''
              + ", systemPrompt=" + systemPrompt
              + ", error='" + error + '\''
              + '}';
    }

    /**
     * Fluent builder interface for conversation states.
     */
    public static class Builder {
        private Map<String, Slot> slots = new HashMap<>();
        private String conversationNode;
        private String action;
        private Prompt systemPrompt;
        private String error;

        /**
         * Include a conversation node in {@code <frame>.<node>} representation
         * in the conversation state.
         *
         * @param node The node represented by the state.
         * @return The current builder state.
         */
        public Builder withNode(String node) {
            this.conversationNode = node;
            return this;
        }

        /**
         * Include an app action and associated slots in the conversation
         * state.
         *
         * @param appAction The action represented by the state.
         * @param args      The slots accompanying the action. These can be
         *                  thought of as arguments for the app action.
         * @return The current builder state.
         */
        public Builder withAction(String appAction, Map<String, Slot> args) {
            this.action = appAction;
            this.slots = args;
            return this;
        }

        /**
         * Include a system prompt in the conversation state.
         *
         * @param prompt The system prompt
         * @return The current builder state.
         */
        public Builder withPrompt(Prompt prompt) {
            this.systemPrompt = prompt;
            return this;
        }

        /**
         * Include an error message in the conversation state.
         *
         * @param errorMessage The error message.
         * @return The current builder state.
         */
        public Builder withError(String errorMessage) {
            this.error = errorMessage;
            return this;
        }

        /**
         * Turn the current builder into an immutable {@code
         * ConversationState}.
         *
         * @return The conversation state represented by the current builder.
         */
        public ConversationState build() {
            return new ConversationState(this);
        }
    }
}
