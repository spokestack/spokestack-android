package io.spokestack.spokestack.dialogue.policy;

import androidx.annotation.NonNull;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.dialogue.ConversationData;
import io.spokestack.spokestack.dialogue.DialogueDispatcher;
import io.spokestack.spokestack.dialogue.DialogueEvent;
import io.spokestack.spokestack.dialogue.DialogueListener;
import io.spokestack.spokestack.dialogue.InMemoryConversationData;
import io.spokestack.spokestack.dialogue.Prompt;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.util.EventTracer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.*;

/**
 * A collection of methods to make rule-based dialogue policy testing more
 * convenient.
 *
 * <p>
 * This class is designed to test a single policy in a single thread and to be
 * used via a static import of all public methods, i.e. <br /> {@code import
 * static io.spokestack.spokestack.dialogue.policy.DialoguePolicyTest.*;}
 * </p>
 *
 * <p>
 * To test a policy, first call {@link #setPolicy(String) setPolicy} to load
 * your policy file. Use {@link #handleIntent(String)} or {@link
 * #handleIntent(String, Map)} to simulate user interactions and {@link
 * #completeTurn(boolean)} (after adding any relevant data via {@link
 * #insertData(String, Object)}) to simulate the app completing user-requested
 * actions.
 * </p>
 *
 * <p>
 * The API for checking conversation results is similar to a mocking framework;
 * this class listens for and stores dialogue events internally, so interactions
 * can be verified using the following methods:
 * </p>
 *
 * <ol>
 *     <li>
 *         {@link #verifyEvent(DialogueEvent.Type, Object) verifyEvent}
 *     </li>
 *     <li>
 *         {@link #verifyEventCount(DialogueEvent.Type, int) verifyEventCount}
 *     </li>
 *     <li>
 *         {@link #verifyPrompt(PromptValidator) verifyPrompt}
 *     </li>
 * </ol>
 *
 * <p>
 * At any time, the interaction history can be cleared with
 * {@link #clearEvents()}. {@link #clearPolicyState()} clears both the
 * interaction history and the internal data store used by the policy and
 * {@link #insertData(String, Object) insertData}. Calling
 * {@link #setPolicy(String) setPolicy} implies the latter.
 * </p>
 *
 * @see RuleBasedDialoguePolicyTest
 */
public final class DialoguePolicyTest {

    private static final TestListener LISTENER = new TestListener();
    private static final int traceLevel = EventTracer.Level.INFO.value();
    private static final DialogueDispatcher DISPATCHER = new DialogueDispatcher(
          traceLevel,
          Collections.singletonList(LISTENER));
    private static ConversationData DATA = new InMemoryConversationData();
    private static RuleBasedDialoguePolicy POLICY;
    private static SpeechConfig SPEECH_CONFIG;

    public static void setPolicy(String policyFile)
          throws IOException {
        if (SPEECH_CONFIG == null) {
            SPEECH_CONFIG = new SpeechConfig();
        }

        SPEECH_CONFIG.put("dialogue-policy-file", policyFile);
        POLICY = new RuleBasedDialoguePolicy(SPEECH_CONFIG);
        clearPolicyState();
    }

    public static RuleBasedDialoguePolicy currentPolicy() {
        return POLICY;
    }

    public static ConversationData dataStore() {
        return DATA;
    }

    public static void handleIntent(String intent) {
        handleIntent(intent, new HashMap<>());
    }

    public static void handleIntent(String intent, Map<String, Slot> slots) {
        if (POLICY == null) {
            fail("no policy loaded");
        }
        NLUResult result = new NLUResult.Builder(intent)
              .withIntent(intent)
              .withSlots(slots)
              .build();
        POLICY.handleTurn(result, DATA, DISPATCHER);
    }

    public static void insertData(String key, Object value) {
        DATA.set(key, value);
    }

    public static void completeTurn(boolean success) {
        if (POLICY == null) {
            fail("no policy loaded");
        }
        POLICY.completeTurn(success, DATA, DISPATCHER);
    }

    public static void clearPolicyState() throws IOException {
        DATA = new InMemoryConversationData();
        POLICY = new RuleBasedDialoguePolicy(SPEECH_CONFIG);
        clearEvents();
    }

    public static void clearEvents() {
        LISTENER.clear();
    }

    /**
     * Set the user's current conversation node. Setting a node explicitly
     * implies clearing the interaction history.
     *
     * @param node The user's current conversation node.
     */
    public static void setNode(String node) {
        LISTENER.clear();
        POLICY.getHistory().setNode(node);
    }

    /**
     * Verify that the specified number of events of the specified type have
     * been received by the listener.
     *
     * @param type  The type of event to count.
     * @param times The total number of {@code type}events that should have
     *              occurred.
     */
    public static void verifyEventCount(DialogueEvent.Type type, int times) {
        List<DialogueEvent> events = new ArrayList<>();
        for (DialogueEvent event : LISTENER.events) {
            if (event.type == type) {
                events.add(event);
            }
        }
        assertEquals(String.format("expected %d %s events; received %d: %s",
              times, type, events.size(), formatList(events)),
              times,
              events.size()
        );
    }

    /**
     * Verify that the specified number of trace messages have been received by
     * the listener.
     *
     * @param count The total number of trace messages that should have been
     *              received.
     */
    public static void verifyTraceCount(int count) {
        assertEquals(
              String.format("expected %d trace messages; received %d: %s",
                    count, LISTENER.traces.size(), formatList(LISTENER.traces)),
              count,
              LISTENER.traces.size());
    }

    public static void verifyEvent(DialogueEvent.Type type, Object eventVal) {
        boolean pass = false;
        for (DialogueEvent event : LISTENER.events) {
            switch (type) {
                case ACTION:
                    pass = Objects.equals(eventVal, event.state.getAction());
                    break;
                case PROMPT:
                    fail("use verifyPrompt for checking prompt events");
                    break;
                case STATE_CHANGE:
                    String fullNode = event.state.getFrameName() + "."
                          + event.state.getNodeName();
                    pass = Objects.equals(eventVal, fullNode);
                    break;
                case ERROR:
                    String err = event.state.getError();
                    pass = err != null && event.state.getError()
                          .matches(String.valueOf(eventVal));
                    break;
            }
            if (pass) {
                break;
            }
        }
        assertTrue(
              String.format("no %s event with \"%s\" value found: %s",
                    type, eventVal, formatList(LISTENER.events)),
              pass);
    }

    /**
     * Verify that a prompt passing the supplied check was received in a
     * dialogue event.
     *
     * @param validator The validation function used to check received prompts.
     */
    public static void verifyPrompt(PromptValidator validator) {
        boolean pass = false;
        List<DialogueEvent> prompts = new ArrayList<>();
        for (DialogueEvent event : LISTENER.events) {
            if (event.type != DialogueEvent.Type.PROMPT) {
                continue;
            }
            prompts.add(event);
            if (validator.promptMatches(event.state.getPrompt(), DATA)) {
                pass = true;
                break;
            }
        }
        assertTrue(
              String.format("no matching prompt found: %s",
                    formatList(prompts)),
              pass);
    }

    /**
     * Verify that a trace message matching the supplied regex was received by
     * the listener.
     *
     * @param regex The regex to check against received trace messages.
     */
    public static void verifyTrace(String regex) {
        boolean pass = false;
        for (String message : LISTENER.traces) {
            if (message.matches(regex)) {
                pass = true;
                break;
            }
        }
        assertTrue(
              String.format("no trace message matching %s found: %s",
                    regex, formatList(LISTENER.traces)),
              pass);
    }

    private static String formatList(List<?> items) {
        StringBuilder builder = new StringBuilder("[");
        for (Object item : items) {
            builder.append("\n\t");
            builder.append(item);
        }
        if (!items.isEmpty()) {
            builder.append("\n");
        }
        builder.append("]");
        return builder.toString();
    }

    public interface PromptValidator {
        boolean promptMatches(Prompt prompt, ConversationData conversationData);
    }


    private static class TestListener implements DialogueListener {
        private final List<DialogueEvent> events = new ArrayList<>();
        private final List<String> traces = new ArrayList<>();

        public void clear() {
            events.clear();
            traces.clear();
        }

        @Override
        public void onDialogueEvent(@NotNull DialogueEvent event) {
            events.add(event);
        }

        @Override
        public void onTrace(@NonNull EventTracer.Level level,
                            @NonNull String message) {
            traces.add(message);
        }
    }
}
