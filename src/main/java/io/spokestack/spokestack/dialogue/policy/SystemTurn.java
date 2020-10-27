package io.spokestack.spokestack.dialogue.policy;

import io.spokestack.spokestack.dialogue.Prompt;

/**
 * A simple container class for data related to a system response in a voice
 * interaction.
 */
public class SystemTurn {

    private Model.AbstractNode node;
    private Prompt prompt;

    /**
     * @return The node or feature associated with this system turn.
     */
    public Model.AbstractNode getNode() {
        return node;
    }

    /**
     * Set the node or feature associated with this system turn.
     * @param nodeOrFeature The node or feature to associate with the turn.
     */
    public void setNode(Model.AbstractNode nodeOrFeature) {
        this.node = nodeOrFeature;
    }

    /**
     * @return The prompt associated with this system turn.
     */
    public Prompt getPrompt() {
        return prompt;
    }

    /**
     * Set the prompt associated with this system turn.
     * @param systemPrompt The prompt to associate with the turn.
     */
    public void setPrompt(Prompt systemPrompt) {
        this.prompt = systemPrompt;
    }
}
