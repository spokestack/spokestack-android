package io.spokestack.spokestack.dialogue.policy;

/**
 * An enumeration of dialogue acts supported by Spokestack.
 */
enum DialogueAct {
    ACCEPT,
    ASK,
    CONFIRM,
    EXIT,
    GREET,
    HELP,
    INFORM,
    READ_SCREEN,
    REJECT,
    REPEAT,
    NAVIGATE,
    COMMAND,
    UNKNOWN;

    static DialogueAct parse(String actName) {
        String normalized = actName.toUpperCase();
        for (DialogueAct act : DialogueAct.values()) {
            if (normalized.equals(act.name())) {
                return act;
            }
        }
        return UNKNOWN;
    }
}
