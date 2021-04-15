package io.spokestack.spokestack.dialogue;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.dialogue.policy.RuleBasedDialoguePolicy;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.util.Callback;
import io.spokestack.spokestack.util.EventTracer;

import java.lang.reflect.InvocationTargetException;
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
 * policy's configuration under the {@code "dialogue-policy-file"} configuration
 * key, or may use a different/custom policy by passing its class name under the
 * {@code "dialogue-policy-class"} key. The class specified by the latter key
 * must contain a single-argument constructor that accepts a {@link
 * SpeechConfig} instance.
 * </p>
 *
 * <p>
 * Dialogue management, like other Spokestack modules, is event-driven.
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

    private final ConversationData dataStore;
    private final DialogueDispatcher eventDispatcher;

    final DialoguePolicy policy;

    private NLUResult lastTurn;

    private DialogueManager(Builder builder) throws Exception {
        this.policy = buildPolicy(builder);
        if (builder.conversationData != null) {
            this.dataStore = builder.conversationData;
        } else {
            this.dataStore = new InMemoryConversationData();
        }
        this.eventDispatcher =
              new DialogueDispatcher(builder.traceLevel, builder.listeners);
    }

    private DialoguePolicy buildPolicy(Builder builder) throws Exception {
        String defaultPolicy = RuleBasedDialoguePolicy.class.getName();
        String policyClass =
              builder.config.getString("dialogue-policy-class", defaultPolicy);

        Object constructed;
        try {
            constructed = Class
                  .forName(policyClass)
                  .getConstructor(SpeechConfig.class)
                  .newInstance(builder.config);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
        return (DialoguePolicy) constructed;
    }

    /**
     * Process a user turn, dispatching dialogue events when system responses
     * are available.
     *
     * @param userTurn The user's request as classified by an NLU system.
     */
    public void processTurn(NLUResult userTurn) {
        try {
            this.lastTurn = userTurn;
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
     *
     * @param success {@code true} if the user's request/desired action was
     *                fulfilled successfully; {@code false} otherwise.
     */
    public void completeTurn(boolean success) {
        this.policy.completeTurn(success, this.dataStore, this.eventDispatcher);
    }

    /**
     * Get the last user turn processed by the dialogue manager.
     *
     * @return The last user turn processed by the dialogue manager.
     */
    public NLUResult getLastTurn() {
        return this.lastTurn;
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
     * Finalize a prompt, interpolating template strings using the current
     * conversation data store.
     *
     * @param prompt The prompt to be finalized.
     * @return The finalized prompt.
     */
    public FinalizedPrompt finalizePrompt(Prompt prompt) {
        return prompt.finalizePrompt(this.dataStore);
    }

    /**
     * Dump the dialogue policy's current state to the currently registered data
     * store. This can be used in conjunction with {@link #load(String) load()}
     * to resume a dialogue in progress in the next app session.
     *
     * <p>
     * Note that, depending on an app's requirements, truly resuming a session
     * in progress may involve persisting and restoring the entire contents of
     * the registered {@code ConversationData} instance, as the dialogue might
     * be relying on data stored there as a result of actions it has instructed
     * the app to perform or data expected by prompts and stored explicitly by
     * the app, etc.
     * </p>
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
     * Add a new listener to receive events from the dialogue management
     * module.
     *
     * @param listener The listener to add.
     */
    public void addListener(DialogueListener listener) {
        this.eventDispatcher.addListener(listener);
    }

    /**
     * Remove a dialogue listener, allowing it to be garbage collected.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(DialogueListener listener) {
        this.eventDispatcher.removeListener(listener);
    }

    /**
     * Dialogue manager builder API.
     */
    public static class Builder {

        private final SpeechConfig config;
        private final List<DialogueListener> listeners = new ArrayList<>();

        private ConversationData conversationData;
        private int traceLevel;

        /**
         * Create a new dialogue manager builder with a default configuration.
         */
        public Builder() {
            this(new SpeechConfig());
        }

        /**
         * Create a new dialogue manager builder with the supplied
         * configuration.
         *
         * @param speechConfig The configuration to use for dialogue
         *                     management.
         */
        public Builder(SpeechConfig speechConfig) {
            this.config = speechConfig;
        }

        /**
         * Sets a configuration value.
         *
         * @param key   configuration property name
         * @param value property value
         * @return the updated builder state
         */
        public Builder setProperty(String key, Object value) {
            this.config.put(key, value);
            return this;
        }

        /**
         * Specify the path to the JSON file containing a Spokestack dialogue
         * policy for the manager to use.
         *
         * <p>
         * This is a convenience method for {@code
         * setProperty("dialogue-policy-file", file)}.
         * </p>
         *
         * @param file Path to a dialogue configuration file.
         * @return the updated builder state
         */
        public Builder withPolicyFile(String file) {
            setProperty("dialogue-policy-file", file);
            return this;
        }

        /**
         * Specify the dialogue policy for the manager to use.
         *
         * <p>
         * This is a convenience method for {@code
         * setProperty("dialogue-policy-class", policyClass)}.
         * </p>
         *
         * @param policyClass The name of the class containing the dialogue
         *                    policy to use.
         * @return the updated builder state
         */
        public Builder withDialoguePolicy(String policyClass) {
            setProperty("dialogue-policy-class", policyClass);
            return this;
        }

        /**
         * @return whether this builder has a dialogue policy enabled via
         * class or JSON file.
         */
        public boolean hasPolicy() {
            return this.config.containsKey("dialogue-policy-file")
                  || this.config.containsKey("dialogye-policy-class");
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
            if (this.config.containsKey("trace-level")) {
                withTraceLevel(this.config.getInteger("trace-level"));
            }
            return new DialogueManager(this);
        }
    }
}
