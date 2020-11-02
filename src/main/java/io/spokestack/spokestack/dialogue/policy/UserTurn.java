package io.spokestack.spokestack.dialogue.policy;

import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.util.Tuple;

import java.util.Map;

/**
 * A container class for extracting and transforming actionable content from an
 * NLU result.
 */
final class UserTurn {
    private final String utterance;
    private final String intent;
    private final DialogueAct dialogueAct;
    private final String detail;
    private final Map<String, Slot> slots;

    UserTurn(String turnUtterance,
             String turnIntent,
             Map<String, Slot> turnSlots) {
        Tuple<DialogueAct, String> parsed = parseIntent(turnIntent);
        this.utterance = turnUtterance;
        this.intent = turnIntent;
        this.dialogueAct = parsed.first();
        this.detail = parsed.second();
        this.slots = turnSlots;
    }

    private Tuple<DialogueAct, String> parseIntent(String userIntent) {
        String verb = userIntent.toUpperCase();
        String turnDetail = null;
        int index = verb.indexOf(".");
        if (index > 0) {
            verb = verb.substring(0, index);
            int lastIndex = userIntent.lastIndexOf("?");
            int detailEnd = Math.max(lastIndex, userIntent.length());
            turnDetail = userIntent.substring(index + 1, detailEnd);
            // from `lastIndex` to the end of the string might be parsed as
            // URL query-style slot key/values in the future
        }

        return new Tuple<>(DialogueAct.parse(verb), turnDetail);
    }

    /**
     * @return The user's original utterance for this turn.
     */
    public String getUtterance() {
        return utterance;
    }

    /**
     * @return The user's intent as determined by the dialogue policy. This may
     * differ from the NLU's classification of the user utterance.
     */
    public String getIntent() {
        return intent;
    }

    /**
     * @return The dialogue act related to the user's intent.
     */
    public DialogueAct getDialogueAct() {
        return dialogueAct;
    }

    /**
     * @return The detail portion of the user's intent. This is used
     * predominately by {@code NAVIGATE} and {@code COMMAND} dialogue acts (to
     * determine a destination node and a feature to be executed,
     * respectively).
     */
    public String getDetail() {
        return detail;
    }

    /**
     * @return The slots related to the user's intent.
     */
    public Map<String, Slot> getSlots() {
        return slots;
    }
}
