package io.spokestack.spokestack.dialogue.policy;

import io.spokestack.spokestack.dialogue.Prompt;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A container class for storing state important to the Spokestack dialogue
 * policy.
 */
class ConversationHistory {

    private final transient Model conversation;
    private Deque<String> path;
    private SystemTurn lastResponse;

    /**
     * Creates a new conversation history.
     *
     * @param conversationConfig The conversation this history is relevant to.
     */
    ConversationHistory(Model conversationConfig) {
        this.path = new ArrayDeque<>();
        this.conversation = conversationConfig;
    }

    /**
     * Forces the conversation path to consist of the supplied node and only
     * that node. Used for testing.
     * <p>
     * Note that this method accepts names instead of IDs in order to make tests
     * more readable, so conversion to IDs happens internally.
     *
     * @param node The node to serve as the conversation path.
     */
    void setNode(String node) {
        setPath(Collections.singletonList(node));
    }

    /**
     * Forces the conversation path to match the supplied path of node names.
     * Used for testing.
     * <p>
     * Note that this method accepts names instead of IDs in order to make tests
     * more readable, so conversion to IDs happens internally.
     *
     * @param nodeIds The desired path through the conversation, listed from
     *                most to least recent node.
     */
    void setPath(List<String> nodeIds) {
        this.path = new ArrayDeque<>();
        for (String nodeName : nodeIds) {
            Model.Node node = this.conversation.lookupNode(nodeName);
            if (node != null) {
                this.path.addFirst(node.getId());
            }
        }
    }

    /**
     * @return The path of node IDs representing the system's part of the
     * interaction up to this point.
     */
    public Deque<String> getPath() {
        return this.path;
    }

    /**
     * Get the ID of the user's current node, ignoring the {@code error}, {@code
     * inform}, and {@code help} frames.
     *
     * @return The ID of the user's current node, or {@code null} if the user's
     * history is empty.
     */
    public String getCurrentNode() {
        for (String node : this.path) {
            if (shouldSkip(node)) {
                continue;
            }
            return node;
        }
        return null;
    }

    /**
     * Get the ID of the user's previous node, ignoring the {@code error},
     * {@code inform}, and {@code help} frames.
     *
     * @return The ID of the user's previous node, or {@code null} if the user's
     * history is empty.
     */
    public String getPreviousNode() {
        String current = getCurrentNode();
        for (String node : this.path) {
            if (node.equals(current) || shouldSkip(node)) {
                continue;
            }
            return node;
        }
        return null;
    }

    public boolean shouldSkip(String nodeId) {
        Model.AbstractNode node = this.conversation.fetchNode(nodeId);
        return node.getName().matches("^(?:error|inform|help).*");
    }

    /**
     * @return The most recent system prompt, if any.
     */
    public Prompt getLastPrompt() {
        if (this.lastResponse == null) {
            return null;
        }
        return this.lastResponse.getPrompt();
    }

    /**
     * Updates the history using the specified conversation state and turn.
     *
     * @param systemTurn The system's response to {@code userTurn}.
     */
    public void update(SystemTurn systemTurn) {
        this.lastResponse = systemTurn;
        if (systemTurn.getNode() != null
              && !(systemTurn.getNode() instanceof Model.Feature)
              && !systemTurn.getNode().getId().equals(getCurrentNode())) {
            updatePath(systemTurn.getNode().getId());
        }
    }

    /**
     * Unconditionally updates the conversation path. Only used apart from the
     * main {@link #update} method when a completed action changes the
     * conversation state.
     *
     * @param nodeId The new conversation state.
     */
    public void updatePath(String nodeId) {
        this.path.addFirst(nodeId);
    }
}
