package io.spokestack.spokestack.dialogue;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * A finalized prompt contains the same fields as a {@link Prompt}, but instead
 * of template placeholders, its contents are fully interpolated strings ready
 * to be displayed to the user or synthesized by TTS.
 */
public final class FinalizedPrompt {
    private final String id;
    private final String text;
    private final String voice;
    private final Proposal proposal;
    private final FinalizedPrompt[] reprompts;
    private final boolean endsConversation;

    private FinalizedPrompt(Builder builder) {
        this.id = builder.id;
        this.text = builder.text;
        if (builder.voice == null) {
            this.voice = builder.text;
        } else {
            this.voice = builder.voice;
        }
        this.proposal = builder.proposal;
        this.reprompts = builder.reprompts;
        this.endsConversation = builder.endsConversation;
    }

    /**
     * @return The prompt's ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Get a version of the prompt formatted for TTS synthesis.
     *
     * @return A version of the prompt formatted for TTS synthesis.
     */
    public String getVoice() {
        return voice;
    }

    /**
     * Get a version of the prompt formatted for print.
     *
     * @return A version of the prompt formatted for print.
     */
    public String getText() {
        return text;
    }

    /**
     * @return this prompt's proposal.
     */
    public Proposal getProposal() {
        return proposal;
    }

    /**
     * @return any reprompts associated with this prompt.
     */
    public FinalizedPrompt[] getReprompts() {
        return reprompts;
    }

    /**
     * @return {@code true} if the conversation should end after the current
     * prompt is delivered; {@code false} otherwise.
     */
    public boolean endsConversation() {
        return endsConversation;
    }

    @Override
    public String toString() {
        return "Prompt{"
              + "id='" + id + '\''
              + ", text='" + text + '\''
              + ", voice='" + voice + '\''
              + ", proposal=" + proposal
              + ", reprompts=" + Arrays.toString(reprompts)
              + ", endsConversation=" + endsConversation
              + '}';
    }

    /**
     * Prompt builder API.
     */
    public static final class Builder {

        private final String id;
        private final String text;
        private String voice;
        private Proposal proposal;
        private FinalizedPrompt[] reprompts;
        private boolean endsConversation;

        /**
         * Create a new prompt builder with the minimal set of required data.
         *
         * @param promptId  The prompt's ID.
         * @param textReply A reply template formatted for print.
         */
        public Builder(@NonNull String promptId, @NonNull String textReply) {
            this.id = promptId;
            this.text = textReply;
            this.reprompts = new FinalizedPrompt[0];
        }

        /**
         * Signals that the prompt to be built should end the conversation with
         * the user.
         *
         * @return the updated builder
         */
        public Builder endsConversation() {
            this.endsConversation = true;
            return this;
        }

        /**
         * Add a reply template formatted for TTS synthesis to the current
         * prompt.
         *
         * @param voiceReply The voice prompt to be added.
         * @return the updated builder
         */
        public Builder withVoice(@NonNull String voiceReply) {
            this.voice = voiceReply;
            return this;
        }

        /**
         * Add a proposal to the current prompt.
         *
         * @param prop The proposal to be added.
         * @return the updated builder
         */
        public Builder withProposal(@NonNull Proposal prop) {
            this.proposal = prop;
            return this;
        }

        /**
         * Specify reprompts for the current prompt.
         *
         * @param prompts The reprompts to attach.
         * @return the updated builder
         */
        public Builder withReprompts(@NonNull List<FinalizedPrompt> prompts) {
            this.reprompts = prompts.toArray(new FinalizedPrompt[0]);
            return this;
        }

        /**
         * @return a complete prompt created from the current builder state.
         */
        public FinalizedPrompt build() {
            return new FinalizedPrompt(this);
        }
    }
}
