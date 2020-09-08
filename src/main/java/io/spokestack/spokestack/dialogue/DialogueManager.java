package io.spokestack.spokestack.dialogue;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.dialogue.policy.RuleBasedDialoguePolicy;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.util.Callback;
import io.spokestack.spokestack.util.EventTracer;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Spokestack's dialogue manager.
 * </p>
 *
 * <p>
 * This component transforms intent and slot results from an {@link
 * io.spokestack.spokestack.nlu.NLUService NLUService} into actions to be
 * performed by the app, including both scene transitions and external requests
 * for data. A system response to the user's request is also included; it can be
 * synthesized and read to the user or printed in a text stream.
 * </p>
 *
 * <p>
 * In order to translate between NLU results and dynamic app actions, the
 * manager relies on a <i>dialogue policy</i>. An app may use Spokestack's
 * rule-based policy by providing the path to a JSON file describing the
 * policy's configuration at build time, or a custom policy may be provided by
 * implementing {@link DialoguePolicy} and passing an instance of the
 * implementing class to the manager's builder.
 * </p>
 *
 * <p>
 * Dialogue management, like other Spokestack subsystems, is event-driven.
 * Registered listeners will receive {@link DialogueEvent DialogueEvents} when
 * the system should make a change based on the user's last request. Events
 * include changes to the app's visual context, prompts to be read or displayed
 * to the user, and actions related to app features that must be executed
 * outside the dialogue manager's control.
 * </p>
 */
public final class DialogueManager implements Callback<NLUResult> {

    /**
     * The prefix used for storing data internal to a dialogue policy in the
     * current data store.
     */
    public static final String SLOT_PREFIX = "_";

    private final DialoguePolicy policy;
    private final ConversationData dataStore;
    private final DialogueDispatcher eventDispatcher;

    private DialogueManager(Builder builder) throws Exception {
        if (builder.policyFile != null) {
            this.policy = new RuleBasedDialoguePolicy(builder.policyFile);
        } else {
            this.policy = builder.dialoguePolicy;
        }
        if (builder.conversationData != null) {
            this.dataStore = builder.conversationData;
        } else {
            this.dataStore = new InMemoryConversationData();
        }
        this.eventDispatcher =
              new DialogueDispatcher(builder.traceLevel, builder.listeners);
    }

    /**
     * Process a user turn, dispatching dialogue events when system responses
     * are available.
     *
     * @param userTurn The user's request as classified by an NLU system.
     */
    public void processTurn(NLUResult userTurn) {
        try {
            policy.handleTurn(userTurn, this.dataStore, this.eventDispatcher);
        } catch (Exception e) {
            eventDispatcher.trace(EventTracer.Level.ERROR,
                  "dialogue error: %s", e.toString());
        }
    }

    /**
     * Complete the pending user turn. Some dialogue policies may not require a
     * signal for turn completion; see the documentation for your chosen policy
     * for more information.
     */
    public void completeTurn() {
        this.policy.completeTurn(this.dataStore, this.eventDispatcher);
    }

    /**
     * @return The currently registered data store. Useful if no custom data
     * store was supplied at build time but dialogue contents need to be
     * persisted externally.
     */
    public ConversationData getDataStore() {
        return this.dataStore;
    }

    /**
     * Dump the dialogue policy's current state to the currently registered data
     * store. This can be used in conjunction with {@link #load(String) load()}
     * to resume a dialogue in progress in the next app session.
     *
     * Note that true cross-session persistence will involve persisting the
     * entire contents of the registered {@code ConversationData} instance, as
     * the dialogue might be relying on data stored there as a result of
     * actions it has instructed the app to perform, etc.
     *
     * @return The serialized state that was dumped to the data store.
     */
    public String dump() {
        return this.policy.dump(this.dataStore);
    }

    /**
     * Attempt to load a saved policy state from the currently registered data
     * store. Assumes that the registered data store contains data stored by
     * calling {@link #dump()} with the same policy as is currently in use.
     *
     * @param state A serialized conversation state from a previous
     *              conversation.
     * @throws Exception if there is an error loading saved state.
     */
    public void load(String state) throws Exception {
        this.policy.load(state, this.dataStore);
    }

    @Override
    public void call(@NonNull NLUResult arg) {
        processTurn(arg);
    }

    @Override
    public void onError(@NonNull Throwable err) {
        eventDispatcher.trace(EventTracer.Level.WARN,
              "NLU classification error: " + err.getLocalizedMessage());
    }

    /**
     * Dialogue manager builder API.
     */
    public static class Builder {

        private String policyFile;
        private DialoguePolicy dialoguePolicy;
        private ConversationData conversationData;
        private int traceLevel;
        private final List<DialogueListener> listeners = new ArrayList<>();

        /**
         * Specify the path to the JSON file containing a Spokestack dialogue
         * policy for the manager to use.
         *
         * @param file Path to a dialogue configuration file.
         * @return the updated builder state
         */
        public Builder withPolicyFile(String file) {
            this.policyFile = file;
            return this;
        }

        /**
         * Specify the dialogue policy for the manager to use.
         *
         * @param policy The dialogue policy to follow for user interactions.
         * @return the updated builder state
         */
        public Builder withCustomPolicy(DialoguePolicy policy) {
            this.dialoguePolicy = policy;
            return this;
        }

        /**
         * Specify the data store to use for conversation data.
         *
         * @param dataStore The data store to use for the conversation.
         * @return the updated builder state
         */
        public Builder withDataStore(ConversationData dataStore) {
            this.conversationData = dataStore;
            return this;
        }

        /**
         * Specify the maximum level of log messages to be delivered to
         * listeners.
         *
         * @param level The maximum log level to deliver.
         * @return the updated builder state
         */
        public Builder withTraceLevel(int level) {
            this.traceLevel = level;
            return this;
        }

        /**
         * Add a listener to receive dialogue events and related log messages.
         *
         * @param listener A dialogue listener.
         * @return the updated builder state
         */
        public Builder addListener(DialogueListener listener) {
            this.listeners.add(listener);
            return this;
        }

        /**
         * Build a dialogue manager that reflects the current builder state.
         *
         * @return A dialogue manager that reflects the current builder state.
         * @throws Exception if there is an error building the manager.
         */
        public DialogueManager build() throws Exception {
            if (this.policyFile == null ^ this.dialoguePolicy == null) {
                return new DialogueManager(this);
            }
            throw new IllegalArgumentException("dialogue manager requires "
                  + "either a policy file or custom policy, but cannot "
                  + "have both");
        }
    }
}
