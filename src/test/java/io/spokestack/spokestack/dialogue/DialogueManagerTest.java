package io.spokestack.spokestack.dialogue;

import com.google.gson.stream.MalformedJsonException;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.util.EventTracer;
import io.spokestack.spokestack.util.Tuple;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DialogueManagerTest {

    @Test
    public void build() throws Exception {
        // no policy
        assertThrows(IllegalArgumentException.class, () ->
              new DialogueManager.Builder().build());

        // bad policy file path
        assertThrows(IOException.class, () ->
              new DialogueManager.Builder()
                    .withPolicyFile("src/test/resources/fake.json")
                    .build());

        // invalid JSON in policy file
        assertThrows(MalformedJsonException.class, () ->
              new DialogueManager.Builder()
                    .withPolicyFile("src/test/resources/invalid.json")
                    .build());

        ConversationData conversationData = new InMemoryConversationData();

        // no data store; an internal store is created automatically
        DialogueManager manager = new DialogueManager.Builder()
              .addListener(new Listener())
              .withPolicyFile("src/test/resources/dialogue.json")
              .withTraceLevel(EventTracer.Level.DEBUG.value())
              .build();

        assertNotEquals(conversationData, manager.getDataStore());

        manager = new DialogueManager.Builder()
              .addListener(new Listener())
              .withPolicyFile("src/test/resources/dialogue.json")
              .withTraceLevel(EventTracer.Level.DEBUG.value())
              .withDataStore(conversationData)
              .build();

        assertEquals(conversationData, manager.getDataStore());
    }

    @Test
    public void dataOperations() throws Exception {
        ConversationData conversationData = new InMemoryConversationData();
        AtomicReference<String> policyState = new AtomicReference<>(null);
        SpeechConfig config = new SpeechConfig();
        config.put("state", policyState);
        String policy = InternalPolicy.class.getName();

        DialogueManager manager = new DialogueManager.Builder(config)
              .withDialoguePolicy(policy)
              .withDataStore(conversationData)
              .build();

        // dump the policy's state and check that it makes it into the data
        // store
        assertNull(conversationData.getFormatted("state",
              ConversationData.Format.TEXT));
        manager.dump();

        assertNull(conversationData.getFormatted("state",
              ConversationData.Format.TEXT));

        // load the saved state into the policy
        policyState.set(null);
        assertNull(manager.dump());

        manager.load(null);
        assertNull(manager.dump());

        manager.load("newState");
        assertEquals("newState", manager.dump());
    }

    @Test
    public void dialogueTurns() throws Exception {
        ConversationData conversationData = new InMemoryConversationData();
        Listener listener = new Listener();
        String policy = InternalPolicy.class.getName();

        DialogueManager manager = new DialogueManager.Builder()
              .addListener(listener)
              .withDialoguePolicy(policy)
              .withTraceLevel(EventTracer.Level.DEBUG.value())
              .withDataStore(conversationData)
              .build();

        assertNull(manager.getLastTurn());

        NLUResult result = new NLUResult.Builder("error").build();

        manager.processTurn(result);
        assertEquals(result, manager.getLastTurn());
        assertEquals(EventTracer.Level.ERROR, listener.traces.get(0).first());
        assertTrue(listener.traces.get(0).second().contains("dialogue error"));
        listener.clear();

        result = new NLUResult.Builder("test")
              .withIntent("intent")
              .build();

        manager.processTurn(result);
        assertEquals(listener.traces.size(), 1);
        assertEquals(listener.events.size(), 0);

        manager.completeTurn(true);
        assertEquals(listener.traces.size(), 1);
        assertEquals(listener.events.size(), 1);
    }

    static class InternalPolicy implements DialoguePolicy {
        AtomicReference<String> state;

        public InternalPolicy(SpeechConfig config) {
            this.state =
                  (AtomicReference<String>) config.getParams().get("state");

            if (this.state == null) {
                this.state = new AtomicReference<>(null);
            }
        }

        @Override
        public String dump(ConversationData conversationData) {
            conversationData.set("state", this.state.get());
            return this.state.get();
        }

        public void load(String state, ConversationData conversationData) {
            this.state.set(state);
        }

        @Override
        public void handleTurn(NLUResult userTurn,
                               ConversationData conversationData,
                               DialogueDispatcher eventDispatcher) {
            if (userTurn.getUtterance().equals("error")) {
                throw new IllegalArgumentException("error");
            } else {
                eventDispatcher.trace(EventTracer.Level.DEBUG, "processing %s",
                      userTurn.getIntent());
            }
        }

        @Override
        public void completeTurn(boolean success, ConversationData conversationData,
                                 DialogueDispatcher eventDispatcher) {
            ConversationState conversationState =
                  new ConversationState.Builder()
                        .withNode(state.get())
                        .withAction("complete", new HashMap<>())
                        .build();
            DialogueEvent event = new DialogueEvent(
                  DialogueEvent.Type.ACTION, conversationState);
            eventDispatcher.dispatch(event);
        }
    }

    static class Listener implements DialogueListener {
        List<DialogueEvent> events = new ArrayList<>();
        List<Tuple<EventTracer.Level, String>> traces = new ArrayList<>();

        public void clear() {
            events.clear();
            traces.clear();
        }

        @Override
        public void onDialogueEvent(@NotNull DialogueEvent event) {
            events.add(event);
        }

        @Override
        public void onTrace(@NotNull EventTracer.Level level,
                            @NotNull String message) {
            traces.add(new Tuple<>(level, message));
        }
    }
}