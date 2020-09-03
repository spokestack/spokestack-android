package io.spokestack.spokestack.dialogue.policy;

import androidx.annotation.NonNull;
import com.google.gson.annotations.SerializedName;
import io.spokestack.spokestack.dialogue.ConversationData;
import io.spokestack.spokestack.dialogue.Prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Classes used for representing the pieces of a rule-based dialogue policy.
 */
public final class Model {
    private static final String BASE_NODE = "__base__";
    private static final Random RANDOM = new Random();
    private final Map<String, AbstractNode> nodeIndex = new HashMap<>();
    private Feature[] features;
    private Frame[] frames;

    /**
     * No-arg constructor used by Gson deserialization.
     */
    public Model() {
    }

    /**
     * Locate the most specific node relevant to the user's request.
     *
     * <p>
     * Currently, {@code help} and {@code inform} dialogue acts route the user
     * to frames whose names match the act, looking for nodes within those
     * frames in the following descending order of specificity:
     * </p>
     *
     * <ol>
     *     <li>{@code <frame>.<node_or_feature_name>.<slot_name>}</li>
     *     <li>{@code <frame>.<node_or_feature_name>}</li>
     *     <li>{@code <frame>.__base__}</li>
     * </ol>
     *
     * <p>
     * In the above examples, {@code node_or_feature_name} refers to the user's
     * conversation state at the time of {@code userTurn}. {@code slot_name} is
     * currently only relevant to the {@code inform} act but may be expanded
     * in the future.
     * </p>
     *
     * @param userTurn The user turn that calls for a special node.
     * @param nodeId   The user's current node in the conversation.
     * @return The special node most relevant to the user's request.
     */
    public Node findSpecialNode(UserTurn userTurn, String nodeId) {
        DialogueAct act = userTurn.getDialogueAct();
        String frameName = (act == DialogueAct.HELP) ? "help" : "inform";
        String nodeName = (nodeId == null) ? "" : "." + fetchNode(nodeId).name;
        Node node = null;

        // get the slot name if applicable
        // if more than one slot key is present in an inform utterance, the
        // first one (according to a lexical sort) is used
        if (act == DialogueAct.INFORM) {
            String tempSlot = null;
            for (String key : userTurn.getSlots().keySet()) {
                if (tempSlot == null || key.compareTo(tempSlot) < 0) {
                    tempSlot = key;
                }
            }
            if (tempSlot != null) {
                String slot = "." + tempSlot;
                node = lookupNode(frameName + nodeName + slot);
            }
        }

        if (node == null) {
            node = lookupNode(frameName + nodeName);
        }
        if (node == null) {
            node = lookupNode(baseNode(frameName));
        }
        return node;
    }

    /**
     * Locate the most specific error node relevant to the user's request.
     *
     * <p>
     * This method acts much like {@link #findSpecialNode(UserTurn, String)
     * findSpecialNode}, looking up nodes on the {@code error} frame in the
     * following descending order of specificity:
     * </p>
     *
     * <ol>
     *     <li>{@code error.<intent_name>.<node_or_feature_name>}</li>
     *     <li>{@code error.<intent_name>}</li>
     *     <li>{@code error.__base__}</li>
     * </ol>
     *
     * <p>
     * In the above examples, {@code node_or_feature_name} refers to the user's
     * conversation state at the time of {@code userTurn}. {@code intent_name}
     * refers to the user's intent.
     * </p>
     *
     * @param userTurn The user turn that calls for a special node.
     * @param nodeId   The user's current node in the conversation.
     * @return The error node most relevant to the user's request.
     */
    public Node findErrorNode(UserTurn userTurn, String nodeId) {
        String frameName = "error";
        Node baseNode = lookupNode(baseNode(frameName));
        if (nodeId == null) {
            return baseNode;
        }
        String intentName = "." + userTurn.getIntent();
        String nodeName = "." + fetchNode(nodeId).name;
        Node node = lookupNode(frameName + intentName + nodeName);

        if (node == null) {
            node = lookupNode(frameName + intentName);
        }
        if (node == null) {
            node = baseNode;
        }
        return node;
    }

    /**
     * Get the name of the base/main {@link Node} for a given frame.
     *
     * @param frameName The name of the frame.
     * @return The name of {@code frameName}'s base node.
     */
    public String baseNode(String frameName) {
        return frameName + ".__base__";
    }

    /**
     * Look up a node given its name.
     *
     * @param nodeName The name of the node to fetch. May be in {@code
     *                 frame.node} format or {@code frame} format for a frame's
     *                 base node.
     * @return The specified node, or {@code null} if no such node exists.
     */
    public Node lookupNode(@NonNull String nodeName) {
        String searchName = nodeName;
        if (!nodeName.contains(".")) {
            searchName += "." + BASE_NODE;
        }
        for (Frame frame : frames) {
            for (Node frameNode : frame.nodes) {
                if (frameNode.getName().equals(searchName)) {
                    return frameNode;
                }
            }
        }
        return null;
    }

    /**
     * Look up a feature given its name.
     *
     * @param featureName The name of the feature to fetch.
     * @param currentNode The ID of the user's current node, to aid in
     *                    disambiguation.
     * @return The specified feature, or {@code null} if no such feature exists.
     */
    public Feature lookupFeature(String featureName, String currentNode) {
        List<Feature> matches = new ArrayList<>();
        for (Feature feature : features) {
            if (feature.getName().equals(featureName)) {
                matches.add(feature);
            }
        }
        switch (matches.size()) {
            case 0:
                return null;
            case 1:
                return matches.get(0);
            default:
                for (Feature feature : features) {
                    if (Objects.equals(feature.source, currentNode)) {
                        return feature;
                    }
                }
        }
        return null;
    }

    /**
     * Fetch a node by ID. "Node" here is used in an abstract sense to refer to
     * the fields that features and nodes contained by frames have in common.
     *
     * @param nodeId The ID of the node to retrieve.
     * @return The specified node, either a feature or an actual node.
     */
    public AbstractNode fetchNode(String nodeId) {
        if (nodeIndex.isEmpty()) {
            indexNodes();
        }
        return nodeIndex.get(nodeId);
    }

    private void indexNodes() {
        for (Feature feature : features) {
            nodeIndex.put(feature.getId(), feature);
        }
        for (Frame frame : frames) {
            for (Node node : frame.nodes) {
                nodeIndex.put(node.getId(), node);
            }
        }
    }

    private static Prompt randomPrompt(Prompt[] prompts) {
        if (prompts.length == 0) {
            return null;
        }
        int randInt = Model.RANDOM.nextInt(prompts.length);
        return prompts[randInt];
    }

    /**
     * A conversation frame is roughly analogous to a screen/{@link
     * android.app.Activity Activity} in an application. A frame contains at
     * least one {@link Node}, named {@code __base__}, that in turn contains the
     * message delivered to the user when they transition to the frame. The
     * frame may have more than one node depending on the information it
     * contains/communicates.
     */
    public static final class Frame {
        private String id;
        private String name;
        private Node[] nodes;

        /**
         * No-arg constructor used by Gson deserialization.
         */
        public Frame() {
        }

        /**
         * @return This frame's ID.
         */
        public String getId() {
            return id;
        }

        /**
         * @return This frame's name.
         */
        public String getName() {
            return name;
        }

        /**
         * @return All nodes attached to this frame.
         */
        public Node[] getNodes() {
            return nodes;
        }
    }

    /**
     * A superclass containing the intersection of fields between {@link Node
     * Nodes} and {@link Feature Features}.
     */
    static class AbstractNode {
        private String id;
        private String name;
        private Prompt[] prompts;
        private Rule[] rules;

        /**
         * No-arg constructor used by Gson deserialization.
         */
        AbstractNode() {
        }

        /**
         * @return This state's ID.
         */
        public String getId() {
            return id;
        }

        /**
         * @return This state's name.
         */
        public String getName() {
            return name;
        }

        /**
         * @return All rules attached to this state.
         */
        public Prompt[] getPrompts() {
            return prompts;
        }

        /**
         * @return All rules attached to this state.
         */
        public Rule[] getRules() {
            return rules;
        }

        /**
         * @return A random prompt attached to this state, or {@code null} if no
         * prompts are associated with it.
         */
        public Prompt randomPrompt() {
            return Model.randomPrompt(this.prompts);
        }

        /**
         * @return {@code true} if this state requires confirmation before
         * leaving (if a node) or executing (if a feature).
         */
        public boolean requiresConfirmation() {
            for (Rule rule : this.rules) {
                if (rule.getType() == Rule.Type.CONFIRMATION) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A node represents a specific conversational state within a broader {@link
     * Frame}. It may or may not entail visual changes in the app. Multiple
     * nodes within the same frame are used primarily to deliver different
     * prompts based on a combination of {@link Rule Rules} and the data
     * available to the dialogue policy at any given time.
     */
    public static final class Node extends AbstractNode {
        private String next;

        /**
         * No-arg constructor used by Gson deserialization.
         */
        public Node() {
        }

        /**
         * @return The ID of the node that most naturally follows this one in
         * conversation, if any.
         */
        public String getNext() {
            return next;
        }
    }

    /**
     * A feature represents the app operating on data outside the control of the
     * dialogue policy. Examples include visual transformations of data and
     * network requests to perform searches with data supplied by the user.
     *
     * <p>
     * Features are not attached hierarchically to any {@link Frame} or {@link
     * Node}, but they may specify a node as a source and/or one as a
     * destination.
     * </p>
     *
     * <p>
     * The source node is used by the dialogue policy to choose among features
     * with the same name; for example, an app that performs two different kinds
     * of search might have two features named {@code select}; the policy would
     * attempt to match the user's current node against each feature's {@code
     * source} field to determine which feature to trigger.
     * </p>
     *
     * <p>
     * The destination node determines the state of the conversation after
     * successful execution of the feature.
     * </p>
     *
     * <p>
     * A host app is instructed to execute a feature by the {@link
     * io.spokestack.spokestack.dialogue.DialogueEvent.Type#ACTION ACTION}
     * event; when the execution is complete, the app should call {@link
     * io.spokestack.spokestack.dialogue.DialogueManager#completeTurn()
     * completeTurn()}.
     * </p>
     */
    public static final class Feature extends AbstractNode {
        private String source;
        private String destination;

        /**
         * No-arg constructor used by Gson deserialization.
         */
        public Feature() {
        }

        /**
         * @return The feature's source node ID, if any.
         */
        public String getSource() {
            return source;
        }

        /**
         * @return The feature's destination node ID, if any.
         */
        public String getDestination() {
            return destination;
        }
    }

    /**
     * A rule alters the conversation flow based on the data available to the
     * policy at runtime. Any number of rules can be attached to a node or a
     * feature; they will be evaluated in the order in which they are declared.
     */
    public static final class Rule {
        private String id;
        private Type type;
        private String key;
        private String[] keys;
        private String value;
        private String redirectTo;
        private Prompt[] prompts;

        /**
         * The types of rules that can be attached to nodes or features.
         */
        public enum Type {

            /**
             * A rule typically attached to features that requires the slot
             * stored in {@link #key} be present before the feature can be
             * executed.
             */
            @SerializedName("slot") SLOT,

            /**
             * A rule typically attached to features that requires one of the
             * slots stored in {@link #keys} be present before the feature can
             * be executed.
             */
            @SerializedName("slot_one_of") SLOT_ONE_OF,

            /**
             * A rule specifying that the system needs extra confirmation from
             * the user before leaving the current conversation state (if the
             * rule is attached to a node) or executing the associated feature.
             */
            @SerializedName("confirmation") CONFIRMATION,

            /**
             * A rule that transforms the intent specified by its {@link #key}
             * into the one specified by its {@link #value}.
             */
            @SerializedName("intent_override") INTENT_OVERRIDE,

            /**
             * A rule that redirects to a specified node ID if its {@link #key}
             * matches its {@link #value}.
             */
            @SerializedName("redirect_positive") REDIRECT_POSITIVE,

            /**
             * A rule that redirects to a specified node ID if its {@link #key}
             * does not match its {@link #value}.
             */
            @SerializedName("redirect_negative") REDIRECT_NEGATIVE,

            /**
             * An unknown rule type, used to avoid errors during
             * deserialization.
             */
            UNKNOWN;

            /**
             * @return {@code true} if this rule redirects to a new conversation
             * node when triggered; {@code false} otherwise.
             */
            public boolean isRedirect() {
                return this == REDIRECT_NEGATIVE || this == REDIRECT_POSITIVE;
            }
        }

        /**
         * No-arg rule constructor used by Gson deserialization.
         */
        public Rule() {
        }

        /**
         * @return This rule's ID.
         */
        public String getId() {
            return id;
        }

        /**
         * @return This rule's type.
         */
        public Type getType() {
            return type;
        }

        /**
         * @return The name of the slot or general data key checked against
         * {@link #value} to determine whether the rule should trigger.
         */
        public String getKey() {
            return key;
        }

        /**
         * @return For {@link Type#SLOT_ONE_OF} rules, a list of slot names. One
         * must be present in the conversation data store to avoid triggering
         * the rule.
         */
        public String[] getKeys() {
            return keys;
        }

        /**
         * @return For redirect rules, the value of {@link #key} that should
         * match or not match in order to trigger a redirect.
         */
        public String getValue() {
            return value;
        }

        /**
         * @return The ID of the node to which this rule redirects when
         * triggered, if any.
         */
        public String getRedirectTo() {
            return redirectTo;
        }

        /**
         * @return The prompts associated with this rule, if any.
         */
        public Prompt[] getPrompts() {
            return prompts;
        }

        /**
         * Determine whether the rule should change the conversation flow based
         * on its criteria and the current state of the conversation.
         *
         * @param userTurn         The current user turn.
         * @param conversationData The conversation data store.
         * @param redirected       Flag representing whether a redirect has
         *                         already been processed for the current turn.
         *                         Only one redirect per turn is honored in
         *                         order to avoid potential redirect cycles.
         * @return {@code true} if the rule should trigger, or cause a change in
         * conversation flow; {@code false} if not.
         */
        public boolean shouldTrigger(UserTurn userTurn,
                                     ConversationData conversationData,
                                     boolean redirected) {
            // only follow one redirect
            if (this.type.isRedirect() && redirected) {
                return false;
            }

            String val = conversationData.getFormatted(this.key,
                  ConversationData.Format.TEXT);
            switch (this.type) {
                case SLOT:
                    // slots are stored with prefixes to avoid collisions
                    val = conversationData.getFormatted(this.key,
                          ConversationData.Format.TEXT);
                    return val == null;
                case SLOT_ONE_OF:
                    for (String slotKey : getKeys()) {
                        val = conversationData.getFormatted(
                              slotKey, ConversationData.Format.TEXT);
                        if (val != null) {
                            return false;
                        }
                    }
                    return true;
                case CONFIRMATION:
                    return userTurn.getDialogueAct() != DialogueAct.CONFIRM;
                case INTENT_OVERRIDE:
                    // intent overrides are processed earlier and ignored here
                    return false;
                case REDIRECT_POSITIVE:
                    return Objects.equals(value, val);
                case REDIRECT_NEGATIVE:
                    return !Objects.equals(value, val);
                default:
                    break;
            }
            return false;
        }

        /**
         * Assuming the rule has been triggered, get the appropriate system
         * response. Some rules do not entail a response, so this may be {@code
         * null}.
         *
         * @param conversation The conversation policy used to retrieve nodes
         *                     and prompts referenced by the rule.
         * @return A system response for this rule.
         */
        public SystemTurn getResponse(Model conversation) {
            SystemTurn response = null;
            switch (type) {
                case SLOT:
                case SLOT_ONE_OF:
                case CONFIRMATION:
                    response = new SystemTurn();
                    response.setPrompt(Model.randomPrompt(this.prompts));
                    break;
                case REDIRECT_POSITIVE:
                case REDIRECT_NEGATIVE:
                    response = new SystemTurn();
                    AbstractNode node = conversation.fetchNode(getRedirectTo());
                    response.setNode(node);
                    response.setPrompt(node.randomPrompt());
                    break;
                case INTENT_OVERRIDE:
                case UNKNOWN:
                default:
                    break;
            }
            return response;
        }
    }
}
