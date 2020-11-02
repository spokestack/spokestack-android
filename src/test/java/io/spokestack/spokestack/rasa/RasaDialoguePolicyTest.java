package io.spokestack.spokestack.rasa;

import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.dialogue.ConversationData;
import io.spokestack.spokestack.dialogue.DialogueDispatcher;
import io.spokestack.spokestack.dialogue.DialogueEvent;
import io.spokestack.spokestack.dialogue.DialogueListener;
import io.spokestack.spokestack.dialogue.InMemoryConversationData;
import io.spokestack.spokestack.dialogue.Prompt;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.util.EventTracer;
import junit.framework.TestListener;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RasaDialoguePolicyTest {

    @Test
    public void unusedMethods() {
        RasaDialoguePolicy policy = new RasaDialoguePolicy(testConfig());
        ConversationData dataStore = new InMemoryConversationData();
        dataStore.set("key", "val");
        assertEquals("", policy.dump(dataStore));
        policy.load("state", dataStore);
    }

    @Test
    public void testEvents() throws InterruptedException {
        RasaDialoguePolicy policy = new RasaDialoguePolicy(testConfig());
        ConversationData dataStore = new InMemoryConversationData();
        TestListener listener = new TestListener();
        DialogueDispatcher dispatcher = testDispatcher(listener);
        // null response throws an error
        String response = null;
        NLUResult result = rasaResult(response);
        policy.handleTurn(result, dataStore, dispatcher);
        DialogueEvent event = listener.events.poll(500, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertEquals(DialogueEvent.Type.ERROR, event.type);

        // empty response throws an error
        response = "{}";
        result = rasaResult(response);
        policy.handleTurn(result, dataStore, dispatcher);
        event = listener.events.poll(500, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertEquals(DialogueEvent.Type.ERROR, event.type);

        String prompt = "hi";
        String imageURL = "https://example.com";
        response = "[" +
              "{\"recipient_id\": \"id\", \"text\": \"" + prompt + "\"}," +
              "{\"recipient_id\": \"id\", \"image\": \"" + imageURL + "\"}," +
              "]";
        result = rasaResult(response);
        policy.handleTurn(result, dataStore, dispatcher);
        List<DialogueEvent> events = new ArrayList<>();
        listener.events.drainTo(events);
        event = events.get(0);
        assertEquals(DialogueEvent.Type.PROMPT, event.type);
        String receivedPrompt = event.state.getPrompt().getText(dataStore);
        assertEquals(prompt, receivedPrompt);
        event = events.get(1);
        assertEquals(DialogueEvent.Type.ACTION, event.type);
        String receivedURL = event.state.getPrompt().getText(dataStore);
        assertEquals(imageURL, receivedURL);
    }

    private SpeechConfig testConfig() {
        SpeechConfig config = new SpeechConfig();
        config.put("sample-rate", 16000);
        config.put("frame-width", 20);
        config.put("buffer-width", 300);
        return config;
    }

    private DialogueDispatcher testDispatcher(DialogueListener listener) {
        int level = EventTracer.Level.INFO.value();
        List<DialogueListener> listeners = new ArrayList<>();
        listeners.add(listener);
        return new DialogueDispatcher(level, listeners);
    }

    private NLUResult rasaResult(String response) {
        HashMap<String, Object> rasaContext = new HashMap<>();
        rasaContext.put(RasaOpenSourceNLU.RESPONSE_KEY, response);
        return new NLUResult.Builder("test utterance")
              .withIntent(RasaOpenSourceNLU.RASA_INTENT)
              .withContext(rasaContext)
              .build();
    }

    static class TestListener implements DialogueListener {
        LinkedBlockingQueue<DialogueEvent> events = new LinkedBlockingQueue<>();

        @Override
        public void onDialogueEvent(@NotNull DialogueEvent event) {
            events.add(event);
        }

        @Override
        public void onTrace(@NotNull EventTracer.Level level,
                            @NotNull String message) {
            // no-op
        }
    }
}