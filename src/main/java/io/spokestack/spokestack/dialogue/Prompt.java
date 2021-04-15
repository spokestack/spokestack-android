package io.spokestack.spokestack.dialogue;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A prompt to be delivered to the user. Prompts can include representations of
 * a system reply formatted for print and for TTS synthesis as well as reprompts
 * to deliver to the user after a period of time has passed and hints for the
 * system about handling expected user responses to the prompt's wording.
 *
 * <p>
 * For an example of the latter, a prompt's text might end in asking the user a
 * yes/no question. The meanings of "yes" and "no" are highly contextual, so
 * prompts that end in such questions should include a {@link Proposal} that
 * includes a contextual interpretation of affirmative and negative responses.
 * </p>
 *
 * <p>
 * Prompts use template placeholders so that dynamic data can be delivered to
 * the user at runtime. Placeholders are strings surrounded by double braces
 * ({@code {{variable}}}), similar to <a href="https://mustache.github.io/">
 * mustache</a> templates, but only variables are supported; no sections,
 * lambdas, etc.
 * </p>
 */
public final class Prompt {
    private static final Pattern PLACEHOLDER_RE =
          Pattern.compile("\\{\\{(.+?)\\}\\}");

    private String id;
    private String text;
    private String voice;
    private Proposal proposal;
    private Prompt[] reprompts;
    private boolean endsConversation;

    /**
     * No-arg constructor used by Gson deserialization.
     */
    public Prompt() {
    }

    private Prompt(Builder builder) {
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
     * @param data The current conversation data, used to expand any template
     *             placeholders in the prompt's text.
     * @return A version of the prompt formatted for TTS synthesis.
     */
    public String getVoice(ConversationData data) {
        return fillTemplate(this.voice, data, ConversationData.Format.VOICE);
    }

    /**
     * Get a version of the prompt formatted for print.
     *
     * @param data The current conversation data, used to expand any template
     *             placeholders in the prompt's text.
     * @return A version of the prompt formatted for print.
     */
    public String getText(ConversationData data) {
        return fillTemplate(this.text, data, ConversationData.Format.TEXT);
    }

    private String fillTemplate(String template, ConversationData data,
                                ConversationData.Format format) {
        Matcher matcher = PLACEHOLDER_RE.matcher(template);
        StringBuffer expanded = new StringBuffer();
        while (matcher.find()) {
            String var = matcher.group(1);
            String expansion = data.getFormatted(var, format);
            // if the key doesn't exist, leave the variable name in as a clue
            // during debugging
            if (expansion == null) {
                expansion = var;
            }
            matcher.appendReplacement(expanded, expansion);
        }
        matcher.appendTail(expanded);
        return expanded.toString();
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
    public Prompt[] getReprompts() {
        return reprompts;
    }

    /**
     * @return {@code true} if the conversation should end after the current
     * prompt is delivered; {@code false} otherwise.
     */
    public boolean endsConversation() {
        return endsConversation;
    }

    /**
     * Finalize this prompt, filling in all placeholders with data from the
     * conversation's data store.
     *
     * @param dataStore The current state of the conversation data to use for
     *                  filling placeholders in prompts.
     * @return A finalized version of this prompt ready for display/synthesis.
     */
    public FinalizedPrompt finalizePrompt(ConversationData dataStore) {
        List<FinalizedPrompt> finalReprompts = new ArrayList<>();
        for (Prompt prompt : this.reprompts) {
            finalReprompts.add(prompt.finalizePrompt(dataStore));
        }

        FinalizedPrompt.Builder builder = new FinalizedPrompt.Builder(
              this.id, this.getText(dataStore))
              .withVoice(this.getVoice(dataStore))
              .withProposal(this.proposal)
              .withReprompts(finalReprompts);

        if (this.endsConversation) {
            builder.endsConversation();
        }
        return builder.build();
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
        private Prompt[] reprompts;
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
            this.reprompts = new Prompt[0];
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
        public Builder withReprompts(@NonNull List<Prompt> prompts) {
            this.reprompts = prompts.toArray(new Prompt[0]);
            return this;
        }

        /**
         * @return a complete prompt created from the current builder state.
         */
        public Prompt build() {
            return new Prompt(this);
        }
    }
}
