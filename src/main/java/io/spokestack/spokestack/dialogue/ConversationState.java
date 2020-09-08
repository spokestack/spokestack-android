package io.spokestack.spokestack.dialogue;


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
    private final Map<String, Object> slots;

    /**
     * Create a new conversation state.
     *
     * @param builder  The current conversation state builder.
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
    public Map<String, Object> getSlots() {
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

    public static class Builder {
        private Map<String, Object> slots = new HashMap<>();
        private String conversationNode;
        private String action;
        private Prompt systemPrompt;
        private String error;

        public Builder withNode(String node) {
            this.conversationNode = node;
            return this;
        }

        public Builder withAction(String appAction) {
            this.action = appAction;
            return this;
        }

        public Builder withAction(String appAction, Map<String, Object> slots) {
            this.action = appAction;
            this.slots = slots;
            return this;
        }

        public Builder withPrompt(Prompt prompt) {
            this.systemPrompt = prompt;
            return this;
        }

        public Builder withError(String errorMessage) {
            this.error = errorMessage;
            return this;
        }

        public ConversationState build() {
            return new ConversationState(this);
        }
    }
}
