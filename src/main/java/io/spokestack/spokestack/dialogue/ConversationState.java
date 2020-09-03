package io.spokestack.spokestack.dialogue;


/**
 * The current state of the conversation at the time of a dialogue event.
 */
public final class ConversationState {

    private final String frameName;
    private final String nodeName;
    private final String appAction;
    private final Prompt systemPrompt;
    private final String error;

    /**
     * Create a new conversation state.
     *
     * @param node         The user's current node, expressed as a dot-separated
     *                     frame and node.
     * @param action       The action just requested by the user, if any.
     * @param prompt       The prompt to be delivered to the user, if any.
     * @param errorMessage The current error message.
     */
    public ConversationState(String node,
                             String action,
                             Prompt prompt,
                             String errorMessage) {
        if (node != null) {
            String[] parts = node.split("\\.", 2);
            this.frameName = parts[0];
            this.nodeName = parts[1];
        } else {
            this.frameName = null;
            this.nodeName = null;
        }
        this.appAction = action;
        this.systemPrompt = prompt;
        this.error = errorMessage;
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
              + ", systemPrompt=" + systemPrompt
              + ", error='" + error + '\''
              + '}';
    }
}
