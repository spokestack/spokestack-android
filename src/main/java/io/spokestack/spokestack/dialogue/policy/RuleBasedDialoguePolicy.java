package io.spokestack.spokestack.dialogue.policy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.MalformedJsonException;
import io.spokestack.spokestack.dialogue.ConversationData;
import io.spokestack.spokestack.dialogue.ConversationState;
import io.spokestack.spokestack.dialogue.DialogueDispatcher;
import io.spokestack.spokestack.dialogue.DialogueEvent;
import io.spokestack.spokestack.dialogue.DialogueManager;
import io.spokestack.spokestack.dialogue.DialoguePolicy;
import io.spokestack.spokestack.dialogue.Prompt;
import io.spokestack.spokestack.dialogue.Proposal;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.util.EventTracer;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import static io.spokestack.spokestack.dialogue.policy.DialogueAct.*;
import static io.spokestack.spokestack.dialogue.policy.Model.*;

/**
 * Spokestack's built-in rule-based dialogue policy.
 *
 * <p>
 * This policy loads a dialogue configuration from a JSON file and determines
 * transitions for the app based on the rules and scenes referenced in that
 * file.
 * </p>
 *
 * <p>
 * See <a href="https://spokestack.io/docs/Concepts/dialogue-management">the
 * online documentation</a> for more information on the Spokestack dialogue
 * format.
 * </p>
 *
 * <p>
 * This dialogue policy employs two-step processing of user requests to allow
 * apps to retrieve or operate on external data before completing a user turn.
 * It will fire {@link DialogueEvent.Type#ACTION ACTION} events to registered
 * dialogue event listeners when an app feature is triggered. When all
 * operations on external data are complete and data relevant to the action has
 * been made accessible via the current {@link ConversationData data store}, the
 * app should call {@link DialogueManager#completeTurn() completeTurn()} on the
 * dialogue manager in use so that any follow-up navigation/prompts can occur.
 * </p>
 */
public class RuleBasedDialoguePolicy implements DialoguePolicy {
    static final String STATE_KEY =
          DialogueManager.SLOT_PREFIX + "policy_state";

    private final Gson gson;
    private final Model conversation;
    private ConversationHistory history;
    private PendingTurn pendingTurn;

    /**
     * Creates a dialogue policy from the supplied file.
     *
     * @param policyFile Path to a JSON configuration for the dialogue.
     * @throws IOException            if there is an error reading the policy
     *                                file.
     * @throws MalformedJsonException if the policy file contains invalid JSON.
     */
    public RuleBasedDialoguePolicy(String policyFile)
          throws IOException, MalformedJsonException {
        this.gson = new GsonBuilder()
              .disableHtmlEscaping()
              .create();
        try (Reader reader = new FileReader(policyFile)) {
            this.conversation = gson.getAdapter(Model.class).fromJson(reader);
        }
        resetHistory();
    }

    private void resetHistory() {
        this.history = new ConversationHistory(this.conversation);
    }

    /**
     * @return the current conversation history. Used for testing.
     */
    ConversationHistory getHistory() {
        return this.history;
    }

    @Override
    public String dump(ConversationData conversationData) {
        HashMap<String, Object> slots = new HashMap<>();
        for (String key : this.history.getSlotKeys()) {
            slots.put(key, conversationData.getFormatted(key,
                  ConversationData.Format.TEXT));
        }
        HashMap<String, Object> state = new HashMap<>();
        state.put("history", this.history);
        state.put("slots", slots);
        String serialized = gson.toJson(state);
        conversationData.set(STATE_KEY, serialized);
        return serialized;
    }

    @Override
    public void load(String state, ConversationData conversationData) {
        if (state == null) {
            return;
        }

        PolicyState deserialized = gson.fromJson(state, PolicyState.class);
        this.history = deserialized.history;
        for (String key : deserialized.slots.keySet()) {
            conversationData.set(key, deserialized.slots.get(key));
        }
    }

    @Override
    public void completeTurn(ConversationData conversationData,
                             DialogueDispatcher eventDispatcher) {
        if (this.pendingTurn == null || !this.pendingTurn.isAction()) {
            return;
        }

        String destination =
              ((Feature) this.pendingTurn.getNode()).getDestination();
        this.pendingTurn = null;

        // if we've changed places in the conversation, fire events notifying
        // the app of both the new state and a prompt related to that state
        if (destination != null
              && !destination.equals(this.history.getCurrentNode())) {
            SystemTurn systemTurn = new SystemTurn();
            AbstractNode node = this.conversation.fetchNode(destination);
            systemTurn.setNode(node);
            finalizeTurn(systemTurn);
            dispatchTurnEvents(null, systemTurn, eventDispatcher);
            this.history.updatePath(destination);
        }
    }

    @Override
    public void handleTurn(NLUResult nluResult,
                           ConversationData conversationData,
                           DialogueDispatcher eventDispatcher) {

        UserTurn userTurn = parseResult(nluResult);

        if (userTurn.getDialogueAct() == DialogueAct.UNKNOWN) {
            dispatchError(eventDispatcher,
                  "unsupported intent: " + nluResult.getIntent());
            return;
        }

        storeSlots(userTurn.getSlots(), conversationData);

        // the first adjustment picks up things like rewrites due to intent
        // proposals; then we have to readjust after dealing with pending
        // state to pick up things like incomplete actions that should lead to
        // changed nodes
        // TODO we should probably just make failing to call completeTurn()
        //  an error condition and not try to correct for it on the next call
        //  to handleTurn()
        userTurn = adjustTurn(userTurn);
        SystemTurn systemTurn =
              clearPendingState(userTurn, conversationData, eventDispatcher);
        userTurn = adjustTurn(userTurn);

        if (systemTurn == null) {
            systemTurn = findTarget(userTurn, eventDispatcher);
            systemTurn = evalRules(userTurn, systemTurn, conversationData);
        }
        finalizeTurn(systemTurn);
        dispatchTurnEvents(userTurn, systemTurn, eventDispatcher);
        updateState(userTurn, systemTurn, conversationData);
    }

    private UserTurn parseResult(NLUResult nluResult) {
        Map<String, Slot> slots = nluResult.getSlots();
        return new UserTurn(nluResult.getUtterance(), nluResult.getIntent(),
              slots);
    }

    private void storeSlots(Map<String, Slot> slots,
                            ConversationData conversationData) {
        for (String key : slots.keySet()) {
            conversationData.set(key, slots.get(key).getValue());
        }
    }

    private SystemTurn clearPendingState(UserTurn userTurn,
                                         ConversationData conversationData,
                                         DialogueDispatcher eventDispatcher) {
        if (this.pendingTurn == null) {
            return null;
        }

        if (this.pendingTurn.isAction() && !this.pendingTurn.failedRule) {
            eventDispatcher.trace(EventTracer.Level.WARN,
                  "incomplete action detected (%s); assuming success",
                  this.pendingTurn.getNode().getName());
            String destination =
                  ((Feature) this.pendingTurn.getNode()).getDestination();
            if (destination != null) {
                this.history.updatePath(destination);
            }
        }

        // only certain intents should cause reevaluation of the pending turn's
        // rules
        SystemTurn response = null;
        if (userTurn.getDialogueAct() == INFORM
              || userTurn.getDialogueAct() == CONFIRM) {
            response = evalRules(
                  userTurn, this.pendingTurn.systemTurn, conversationData);
        }
        this.pendingTurn = null;
        return response;
    }

    private UserTurn adjustTurn(UserTurn userTurn) {
        DialogueAct originalAct = userTurn.getDialogueAct();
        String intent = getIntentOverride(userTurn.getIntent());
        intent = processProposalFollowups(intent, originalAct);
        return new UserTurn(
              userTurn.getUtterance(), intent, userTurn.getSlots());
    }

    private String getIntentOverride(String original) {
        String nodeId = this.history.getCurrentNode();
        if (nodeId != null) {
            AbstractNode currentNode = this.conversation.fetchNode(nodeId);
            for (Rule rule : currentNode.getRules()) {
                if (rule.getType() == Rule.Type.INTENT_OVERRIDE
                      && rule.getKey().equals(original)) {
                    return rule.getValue();
                }
            }
        }

        return original;
    }

    private String processProposalFollowups(String original, DialogueAct act) {
        if (act != ACCEPT && act != REJECT) {
            return original;
        }
        String adjusted = null;
        Prompt lastPrompt = this.history.getLastPrompt();
        if (lastPrompt != null) {
            Proposal proposal = this.history.getLastPrompt().getProposal();
            if (proposal != null) {
                adjusted = (act == ACCEPT)
                      ? proposal.getAccept()
                      : proposal.getReject();
            }
        }
        return (adjusted != null) ? adjusted : getDefaultProposalIntent(act);
    }

    private String getDefaultProposalIntent(DialogueAct act) {
        return (act == ACCEPT) ? "navigate.next" : "navigate.back";
    }

    private SystemTurn findTarget(UserTurn userTurn,
                                  DialogueDispatcher eventDispatcher) {
        SystemTurn turn = new SystemTurn();
        String newNode = null;
        String nodeId = this.history.getCurrentNode();
        switch (userTurn.getDialogueAct()) {
            case EXIT:
                newNode = this.conversation.baseNode("exit");
                break;
            case GREET:
                newNode = this.conversation.baseNode("greet");
                break;
            case NAVIGATE:
                newNode = getNavigationTarget(userTurn, eventDispatcher);
                break;
            case COMMAND:
                String featureName = userTurn.getDetail();
                Feature feature =
                      this.conversation.lookupFeature(featureName, nodeId);
                if (feature == null) {
                    dispatchError(eventDispatcher, "missing feature: "
                          + featureName);
                } else {
                    turn.setNode(feature);
//                    this.pendingTurn = new PendingTurn(userTurn, turn, false);
                }
                break;
            case HELP:
            case INFORM:
                Node specialNode =
                      this.conversation.findSpecialNode(userTurn, nodeId);
                if (specialNode == null) {
                    dispatchError(eventDispatcher, "missing frame: "
                          + userTurn.getDialogueAct().name().toLowerCase());
                } else {
                    newNode = specialNode.getName();
                }
                break;
            case REPEAT:
                turn.setPrompt(this.history.getLastPrompt());
                break;
            case READ_SCREEN:
                // TODO figure out prompt here --
                //  a designated node scheme within help?
            case ACCEPT:
            case REJECT:
            case CONFIRM:
            case ASK:
            case UNKNOWN:
                // - accept and reject are impossible after adjustment
                // - confirm is handled as part of a pending turn
                //   and can't be produced by the NLU
                // - others stay on the current node
            default:
                dispatchError(eventDispatcher, "unexpected intent: "
                      + userTurn.getIntent());
                break;
        }

        // find the node's ID
        if (turn.getNode() == null) {
            AbstractNode node = null;
            if (newNode != null) {
                node = this.conversation.lookupNode(newNode);
                if (node == null) {
                    dispatchError(eventDispatcher, "missing node: " + newNode);
                    String current = this.history.getCurrentNode();
                    node = findErrorNode(userTurn, current, eventDispatcher);
                }
            }
            turn.setNode(node);
        }

        return turn;
    }

    /**
     * Get the ID of the node targeted by a navigation intent.
     * <p>
     * Special cases are handled first and result in navigation to an error node
     * if any necessary preconditions are not met.
     *
     * @param userTurn        The full user turn.
     * @param eventDispatcher Event dispatcher for reporting errors.
     * @return The ID of the node resulting from the navigation.
     */
    private String getNavigationTarget(UserTurn userTurn,
                                       DialogueDispatcher eventDispatcher) {
        // check navigation special cases
        String currentNodeId = this.history.getCurrentNode();
        String nodeName = userTurn.getDetail();
        AbstractNode node = this.conversation.lookupNode(nodeName);
        switch (nodeName) {
            case "back":
                String prevId = this.history.getPreviousNode();
                if (prevId == null) {
                    node = findErrorNode(
                          userTurn, currentNodeId, eventDispatcher);
                } else {
                    node = this.conversation.fetchNode(prevId);
                }
                break;
            case "next":
                if (currentNodeId == null) {
                    node = findErrorNode(userTurn, null, eventDispatcher);
                } else {
                    Node currentNode =
                          (Node) this.conversation.fetchNode(currentNodeId);
                    String nextId = currentNode.getNext();
                    if (nextId == null) {
                        node = findErrorNode(
                              userTurn, currentNodeId, eventDispatcher);
                    } else {
                        node = this.conversation.fetchNode(nextId);
                    }
                }
                break;
            case "reset":
                Node firstNode = this.conversation.lookupNode("greet");
                if (firstNode == null) {
                    node = findErrorNode(
                          userTurn, currentNodeId, eventDispatcher);
                } else {
                    node = firstNode;
                }
                resetHistory();
                break;
            default:
                break;
        }

        return (node != null) ? node.getName() : null;
    }

    private AbstractNode findErrorNode(UserTurn userTurn,
                                       String currentNode,
                                       DialogueDispatcher eventDispatcher) {
        Node errorNode = this.conversation.findErrorNode(
              userTurn, currentNode);
        if (errorNode == null) {
            dispatchError(eventDispatcher, "missing frame: error");
            return null;
        }
        return errorNode;
    }

    private void dispatchError(DialogueDispatcher eventDispatcher,
                               String message) {
        ConversationState state =
              new ConversationState(null, null, null, message);
        DialogueEvent event =
              new DialogueEvent(DialogueEvent.Type.ERROR, state);
        eventDispatcher.dispatch(event);
    }

    private SystemTurn evalRules(UserTurn userTurn,
                                 SystemTurn systemTurn,
                                 ConversationData conversationData) {
        AbstractNode node = systemTurn.getNode();

//        if (this.pendingTurn != null) {
        // actions get priority since the system turn won't get a node
        // for them
//            node = this.pendingTurn.node;
//        }

        if (node == null) {
            String currentNode = this.history.getCurrentNode();
            // if we don't have a turn node or current node,
            // there won't be any rules to check
            if (currentNode == null) {
                return systemTurn;
            } else {
                node = this.conversation.fetchNode(currentNode);
            }
        }

        SystemTurn systemResponse = checkRules(
              userTurn, systemTurn, node, conversationData, false);

        AbstractNode newNode = systemResponse.getNode();
        if (newNode != null && newNode != node) {
            systemResponse = checkRules(
                  userTurn, systemResponse, newNode, conversationData, true);
        }
        return systemResponse;
    }

    private SystemTurn checkRules(
          UserTurn userTurn,
          SystemTurn systemTurn,
          AbstractNode node,
          ConversationData conversationData,
          boolean redirected) {
        Rule[] rules = node.getRules();
        SystemTurn systemResponse = systemTurn;
        boolean ruleTriggered = false;
        for (Rule rule : rules) {
            if (rule.shouldTrigger(userTurn, conversationData, redirected)) {
                systemResponse = rule.getResponse(this.conversation);
                ruleTriggered = true;
                break;
            }
        }
        if (ruleTriggered) {
            this.pendingTurn = new PendingTurn(userTurn, systemTurn, true);
        }
        return systemResponse;
    }

    private void finalizeTurn(SystemTurn systemTurn) {
        AbstractNode node = systemTurn.getNode();
        if (node != null
              && !node.getId().equals(this.history.getCurrentNode())) {
            if (systemTurn.getPrompt() == null) {
                Prompt prompt = node.randomPrompt();
                systemTurn.setPrompt(prompt);
            }
        } else {
            systemTurn.setNode(null);
        }
    }

    private void dispatchTurnEvents(UserTurn userTurn,
                                    SystemTurn systemTurn,
                                    DialogueDispatcher eventDispatcher) {
        ConversationState state = createEventState(systemTurn);
        DialogueEvent event;
        if (state.getPrompt() != null) {
            event = new DialogueEvent(DialogueEvent.Type.PROMPT, state);
            eventDispatcher.dispatch(event);
        }
        if (state.getAction() != null) {
            if (this.pendingTurn == null) {
                this.pendingTurn = new PendingTurn(userTurn, systemTurn, false);
            }
            event = new DialogueEvent(DialogueEvent.Type.ACTION, state);
            eventDispatcher.dispatch(event);
        }
        if (state.getNodeName() != null) {
            event = new DialogueEvent(DialogueEvent.Type.STATE_CHANGE, state);
            eventDispatcher.dispatch(event);
        }
    }

    private ConversationState createEventState(SystemTurn systemTurn) {
        String action = null;
        String nodeName = null;
        AbstractNode node = systemTurn.getNode();
        if (node instanceof Feature) {
            action = node.getName();
        } else if (node != null) {
            nodeName = node.getName();
        }
        Prompt prompt = systemTurn.getPrompt();

        return new ConversationState(nodeName, action, prompt, null);
    }

    private void updateState(UserTurn userTurn,
                             SystemTurn systemTurn,
                             ConversationData conversationData) {
        conversationData.set(
              DialogueManager.SLOT_PREFIX + "last_intent",
              userTurn.getIntent());
        this.history.update(userTurn, systemTurn);
    }

    /**
     * Internal class for tracking turns that require one or more followup
     * interactions to complete. The happy-path system response must be
     * maintained across interactions; it will either be delivered when all
     * rules are satisfied or discarded if a future user turn changes the
     * subject.
     */
    static class PendingTurn {
        final UserTurn turn;
        final SystemTurn systemTurn;
        final boolean failedRule;

        PendingTurn(UserTurn userTurn,
                    SystemTurn response,
                    boolean didFail) {
            this.turn = userTurn;
            this.systemTurn = response;
            this.failedRule = didFail;
        }

        public AbstractNode getNode() {
            return systemTurn.getNode();
        }

        public boolean isAction() {
            return systemTurn.getNode() != null
                  && systemTurn.getNode() instanceof Feature;
        }
    }

    /**
     * A container class used to serialize and deserialize internal policy
     * state.
     */
    private static class PolicyState {
        final ConversationHistory history;
        final Map<String, String> slots;

        /**
         * Create a new complete state.
         *
         * @param conversationHistory The current conversation history.
         * @param currentSlots        A map of slot names to string values that
         *                            have been provided so far in the
         *                            conversation.
         */
        PolicyState(ConversationHistory conversationHistory,
                    Map<String, String> currentSlots) {
            this.history = conversationHistory;
            this.slots = currentSlots;
        }
    }
}
