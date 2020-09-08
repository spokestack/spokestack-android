package io.spokestack.spokestack.dialogue.policy;

import io.spokestack.spokestack.dialogue.ConversationData;
import io.spokestack.spokestack.dialogue.DialogueEvent;
import io.spokestack.spokestack.dialogue.InMemoryConversationData;
import io.spokestack.spokestack.nlu.Slot;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.spokestack.spokestack.dialogue.policy.DialoguePolicyTest.*;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for Spokestack's rule-based dialogue policy using a hand-crafted test
 * policy file. Contains example usage of {@link DialoguePolicyTest} utilities.
 */
public class RuleBasedDialoguePolicyTest {

    @Test
    public void statePersistence() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        Map<String, Slot> slots = new HashMap<>();
        String slotKey = "testSlot";
        Slot slot = new Slot(slotKey, "value", "value");
        slots.put(slotKey, slot);
        handleIntent("greet", slots);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        RuleBasedDialoguePolicy policy = currentPolicy();
        ConversationData data = dataStore();
        assertNull(data.get(RuleBasedDialoguePolicy.STATE_KEY));

        // nothing to load...yet
        assertThrows(NullPointerException.class, () -> policy.load("", data));
        assertNull(data.get(RuleBasedDialoguePolicy.STATE_KEY));

        String dumped = policy.dump(data);
        assertNotNull(data.get(RuleBasedDialoguePolicy.STATE_KEY));

        ConversationData emptyData = new InMemoryConversationData();
        assertNull(emptyData.get(slotKey));
        clearPolicyState();
        RuleBasedDialoguePolicy newPolicy = currentPolicy();
        ConversationHistory history = newPolicy.getHistory();

        assertTrue(history.getPath().isEmpty());
        assertTrue(history.getSlotKeys().isEmpty());
        newPolicy.load(dumped, emptyData);

        // the internal history gets completely overwritten, so we have to
        // re-retrieve it
        history = newPolicy.getHistory();
        assertFalse(history.getPath().isEmpty());
        assertFalse(history.getSlotKeys().isEmpty());
        assertEquals(slot, emptyData.get(slotKey));

        // dump and load just using the state itself
        dumped = newPolicy.dump(emptyData);
        clearPolicyState();
        newPolicy = currentPolicy();
        assertTrue(newPolicy.getHistory().getPath().isEmpty());

        newPolicy.load(dumped, emptyData);
        assertFalse(newPolicy.getHistory().getPath().isEmpty());
    }

    @Test
    public void incompletePolicy() throws IOException {
        setPolicy("src/test/resources/incomplete_dialogue.json");

        // invalid feature
        handleIntent("command.invalid");
        verifyEvent(DialogueEvent.Type.ERROR, "missing feature: invalid");

        handleIntent("help");
        verifyEvent(DialogueEvent.Type.ERROR, "missing frame: help");

        Map<String, Slot> slots = new HashMap<>();
        slots.put("name", new Slot("huh", "what", "slim shady"));
        handleIntent("inform", slots);
        verifyEvent(DialogueEvent.Type.ERROR, "missing frame: inform");

        handleIntent("exit");
        verifyEvent(DialogueEvent.Type.ERROR, "missing node: exit.__base__");
        // the system attempts to pull back an error message to read to the
        // user, but this dialogue is so degenerate that the error node is
        // also missing
        verifyEvent(DialogueEvent.Type.ERROR, "missing frame: error");

        // the same thing happens for the greet frame, which is where reset
        // attempts to navigate to
        clearEvents();
        handleIntent("navigate.reset", slots);
        verifyEvent(DialogueEvent.Type.ERROR, "missing frame: error");
    }

    @Test
    public void invalidIntents() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        handleIntent("question");
        verifyEvent(DialogueEvent.Type.ERROR, "unsupported intent.*");
    }

    @Test
    public void basicNavigation() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        // "next" in a fresh conversation triggers an error
        handleIntent("navigate.next");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "error.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Sorry, Dave"));
        clearEvents();

        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        // nothing to do here, so no events should happen
        completeTurn(true);
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 1);
        verifyEventCount(DialogueEvent.Type.PROMPT, 1);

        // accept uses default handling of navigate.next
        handleIntent("accept");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "frame_1.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getVoice(data).contains("frame one")
                    && prompt.getText(data).contains("frame 1"));

        // no next node specified, so we get an error
        handleIntent("navigate.next");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "error.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Sorry, Dave"));

        clearEvents();
        // "start over" clears the internal conversation history
        handleIntent("navigate.reset");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Hi"));

        // so the "back" intent has nowhere to go back to
        handleIntent("navigate.back");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "error.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Sorry, Dave"));

        verifyEventCount(DialogueEvent.Type.ERROR, 0);
    }

    @Test
    public void featureInvocation() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        handleIntent("command.play");
        verifyEvent(DialogueEvent.Type.ACTION, "play");

        // no state changes until turn completion
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "player.__base__");
        verifyEventCount(DialogueEvent.Type.PROMPT, 0);

        // clear events to cleanly assert that no state change happens for the
        // next intent
        clearEvents();

        handleIntent("navigate.next");
        // the navigation intent is overridden to stay on the same frame
        verifyEvent(DialogueEvent.Type.ACTION, "next_song");
        completeTurn(true);
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);
        verifyEventCount(DialogueEvent.Type.PROMPT, 0);

        // the stop command plays an immediate prompt (for some reason)
        // and navigates back to greet on completion
        handleIntent("command.stop");
        verifyEvent(DialogueEvent.Type.ACTION, "stop");
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);
        verifyEventCount(DialogueEvent.Type.PROMPT, 1);
        verifyPrompt((prompt, data) ->
              prompt.getVoice(data).contains("Stopping"));
        completeTurn(true);
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 1);
        verifyEventCount(DialogueEvent.Type.PROMPT, 2);
        verifyPrompt((prompt, data) ->
              prompt.getVoice(data).contains("Hi"));


        clearEvents();
        handleIntent("command.play");
        verifyEvent(DialogueEvent.Type.ACTION, "play");

        // no state changes until turn completion
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        // a new intent before turn completion prevents the state change event
        handleIntent("navigate.next");
        verifyTrace("incomplete action.*play.*");
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        // we've created a strange sequence, but the policy can
        // handle it -- the dialogue policy assumes that the state change
        // for the play action happened, so we're internally on the player
        // frame; we won't get a state change event until we leave it
        completeTurn(true);
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        handleIntent("command.stop");
        verifyEvent(DialogueEvent.Type.ACTION, "stop");
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);
        verifyEventCount(DialogueEvent.Type.PROMPT, 1);
        completeTurn(true);
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 1);
        verifyEventCount(DialogueEvent.Type.PROMPT, 2);

        verifyEventCount(DialogueEvent.Type.ERROR, 0);
    }

    @Test
    public void featureError() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        handleIntent("command.play");
        verifyEvent(DialogueEvent.Type.ACTION, "play");

        // no state changes until turn completion
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        completeTurn(false);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "error.__base__");
        verifyEventCount(DialogueEvent.Type.PROMPT, 1);
    }

    @Test
    public void repeat() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 1);
        verifyEventCount(DialogueEvent.Type.PROMPT, 1);
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        clearEvents();
        verifyEventCount(DialogueEvent.Type.PROMPT, 0);
        handleIntent("repeat");
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);
        verifyEventCount(DialogueEvent.Type.PROMPT, 1);
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        verifyEventCount(DialogueEvent.Type.ERROR, 0);
    }

    @Test
    public void defaultProposals() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        clearEvents();
        handleIntent("command.play");
        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "player.__base__");
        verifyEventCount(DialogueEvent.Type.PROMPT, 0);

        clearEvents();
        handleIntent("reject");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));
    }

    @Test
    public void explicitProposals() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        handleIntent("navigate.next");
        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "frame_1.__base__");
        verifyEventCount(DialogueEvent.Type.PROMPT, 2);

        // on frame_1, accept overrides the default to actually go back
        clearEvents();
        handleIntent("accept");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyEventCount(DialogueEvent.Type.PROMPT, 1);

        // try again, this time saying no to the proposal, which exits
        clearPolicyState();

        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        handleIntent("navigate.next");
        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "frame_1.__base__");
        verifyEventCount(DialogueEvent.Type.PROMPT, 2);

        handleIntent("reject");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "exit.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getVoice(data).contains("Later"));
    }

    @Test
    public void specialNodes() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        Map<String, Slot> slots = new HashMap<>();
        slots.put("name", new Slot("huh", "what", "slim shady"));
        handleIntent("inform", slots);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "inform.greet.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getVoice(data).contains("Sure"));

        slots.clear();
        slots.put("yeet", new Slot("huh", "what", "slim shady"));
        handleIntent("inform", slots);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE,
              "inform.greet.__base__.yeet");
        verifyPrompt((prompt, data) ->
              prompt.getVoice(data).contains("Yeah fam"));

        handleIntent("help");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "help.__base__");

        // the help and inform "frames" are ignored because they're not likely
        // to have a visual analogue, so throw in another intent to get us
        // off the greet frame onto one with no specific inform nodes
        handleIntent("command.play");
        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "player.__base__");

        // we'll land on the default inform node
        handleIntent("inform", slots);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "inform.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getVoice(data).contains("Got it"));

        handleIntent("exit");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "exit.__base__");
        verifyPrompt((prompt, data) -> prompt.endsConversation());
    }

    @Test
    public void confirmationRule() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        handleIntent("command.buy_album");
        // confirmation rule prevents the action event
        verifyEventCount(DialogueEvent.Type.ACTION, 0);

        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("sure?"));

        handleIntent("accept");
        verifyEvent(DialogueEvent.Type.ACTION, "buy_album");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("OK"));

        // try again with rejection
        clearPolicyState();

        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");
        verifyPrompt((prompt, data) -> prompt.getVoice(data).contains("Hi")
              && prompt.getText(data).contains("Hi"));

        handleIntent("command.buy_album");
        verifyEventCount(DialogueEvent.Type.ACTION, 0);

        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("sure?"));

        handleIntent("reject");
        verifyEventCount(DialogueEvent.Type.ACTION, 0);
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Hi"));


        // a feature requiring confirmation that also has a destination node
        clearPolicyState();

        handleIntent("command.play");
        verifyEventCount(DialogueEvent.Type.ACTION, 1);
        completeTurn(true);
        verifyEventCount(DialogueEvent.Type.ACTION, 1);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "player.__base__");

        handleIntent("command.exit_player");

        verifyEventCount(DialogueEvent.Type.ACTION, 1);
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("sure?"));

        clearEvents();
        handleIntent("accept");
        verifyEventCount(DialogueEvent.Type.ACTION, 1);
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Exiting"));

        completeTurn(true);
        verifyEventCount(DialogueEvent.Type.ACTION, 1);
        verifyTraceCount(0);
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Hi"));
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.__base__");

    }

    @Test
    public void ruleTypes() throws IOException {
        setPolicy("src/test/resources/rule-dialogue.json");

        // positive redirect
        setNode("greet.__base__");
        insertData("new_user", true);
        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.new_user");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Welcome"));

        // negative redirect
        setNode("greet.__base__");
        insertData("visits", 2);
        handleIntent("greet");

        // rules are processed in order, so since we didn't clear the
        // data store, the first one still triggers
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.new_user");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Welcome"));

        // clear everything out and try again
        clearPolicyState();
        setNode("greet.__base__");
        insertData("visits", 2);
        handleIntent("greet");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.return_user");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Thanks"));

        // slot filling
        setNode("greet.__base__");
        handleIntent("command.play");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("song to play"));
        verifyEventCount(DialogueEvent.Type.ACTION, 0);
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        Map<String, Slot> slots = new HashMap<>();
        String song = "Virtute at Rest";
        Slot slot = new Slot("song", "entity", song, song);
        slots.put("song", slot);
        handleIntent("inform", slots);
        verifyEvent(DialogueEvent.Type.ACTION, "play");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains(song));
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "player.__base__");

        // accept any of a list of slots
        setNode("greet.__base__");
        handleIntent("command.search");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("artist or an album"));
        verifyEventCount(DialogueEvent.Type.ACTION, 0);
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        slots.clear();
        slot = new Slot("artist", "entity", "John K. Samson", "John K. Samson");
        slots.put("artist", slot);
        handleIntent("inform", slots);
        verifyEvent(DialogueEvent.Type.ACTION, "search");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("searching"));
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);

        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "player.__base__");

        // two redirects -- only the first is followed, in order to avoid
        // potential cycles
        clearPolicyState();
        setNode("greet.__base__");
        insertData("visits", 3);
        handleIntent("greet");
        // this would be player.__base__ if the second redirect had worked
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "greet.return_user");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("Thanks"));

        // the redirect on return_user is followed if all else is equal but
        // a redirect hasn't already been followed
        clearPolicyState();
        setNode("greet.__base__");
        insertData("visits", 3);
        handleIntent("navigate.greet.return_user");
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "player.__base__");
        verifyEventCount(DialogueEvent.Type.PROMPT, 0);
    }

    @Test
    public void duplicateFeatures() throws IOException {
        setPolicy("src/test/resources/feature-dialogue.json");

        // note the different starting nodes; these determine the different
        // destinations (STATE_CHANGE events) given the same intent name.

        setNode("search_results.__base__");
        handleIntent("command.select");
        verifyEvent(DialogueEvent.Type.ACTION, "select");
        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "details.__base__");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("first"));

        clearEvents();
        setNode("search_results.two");
        handleIntent("command.select");
        verifyEvent(DialogueEvent.Type.ACTION, "select");
        completeTurn(true);
        verifyEvent(DialogueEvent.Type.STATE_CHANGE, "details.two");
        verifyPrompt((prompt, data) ->
              prompt.getText(data).contains("second"));
    }

    @Test
    public void unsupportedIntent() throws IOException {
        setPolicy("src/test/resources/dialogue.json");

        setNode("greet.__base__");
        handleIntent("ask");
        verifyEventCount(DialogueEvent.Type.ACTION, 0);
        verifyEventCount(DialogueEvent.Type.STATE_CHANGE, 0);
        verifyEventCount(DialogueEvent.Type.PROMPT, 0);
        verifyEvent(DialogueEvent.Type.ERROR, "unexpected intent.*");
    }

}