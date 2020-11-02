package io.spokestack.spokestack.rasa;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.dialogue.ConversationData;
import io.spokestack.spokestack.dialogue.ConversationState;
import io.spokestack.spokestack.dialogue.DialogueDispatcher;
import io.spokestack.spokestack.dialogue.DialogueEvent;
import io.spokestack.spokestack.dialogue.DialoguePolicy;
import io.spokestack.spokestack.dialogue.Prompt;
import io.spokestack.spokestack.nlu.NLUResult;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A dialogue policy that examines the response from a Rasa Open Source server
 * retrieved by a {@link RasaOpenSourceNLU} component and dispatches events
 * based on its contents.
 *
 * <p>
 * A {@code text} response is dispatched as a {@code PROMPT} event, and an image
 * is dispatched as an {@code ACTION} event with an {@code appAction} of {@code
 * "displayImage"} and a {@code systemPrompt} containing the image URL.
 * </p>
 *
 * <p>
 * Intents that do not match the one produced by {@link RasaOpenSourceNLU} are
 * not supported and will result in {@code ERROR} events.
 * </p>
 */
public final class RasaDialoguePolicy implements DialoguePolicy {
    private static final String IMAGE_ACTION = "displayImage";

    private final Gson gson;

    /**
     * Create a new dialogue policy for handling responses from Rasa Open
     * Source.
     *
     * @param speechConfig configuration properties for this policy.
     */
    public RasaDialoguePolicy(SpeechConfig speechConfig) {
        this.gson = new Gson();
    }

    @Override
    public String dump(ConversationData conversationData) {
        // unnecessary - all state is managed on the Rasa server
        return "";
    }

    @Override
    public void load(String state, ConversationData conversationData) {
        // unnecessary - all state is managed on the Rasa server
    }

    @Override
    public void handleTurn(
          NLUResult userTurn,
          ConversationData conversationData,
          DialogueDispatcher eventDispatcher) {
        String intent = userTurn.getIntent();
        if (!intent.equals(RasaOpenSourceNLU.RASA_INTENT)) {
            // we can't handle non-Rasa intents
            dispatchError(eventDispatcher, "non-Rasa intent: " + intent);
        }

        List<RasaResponse> responses = getResponses(userTurn, eventDispatcher);
        for (RasaResponse response : responses) {
            // guard against trailing commas in the json
            if (response != null) {
                dispatchResponse(eventDispatcher, response);
            }
        }
    }

    private void dispatchError(DialogueDispatcher dispatcher, String msg) {
        ConversationState state = new ConversationState.Builder()
              .withError(msg)
              .build();
        DialogueEvent event =
              new DialogueEvent(DialogueEvent.Type.ERROR, state);
        dispatcher.dispatch(event);
    }

    private List<RasaResponse> getResponses(NLUResult userTurn,
                                            DialogueDispatcher dispatcher) {
        Object response = userTurn.getContext()
              .get(RasaOpenSourceNLU.RESPONSE_KEY);
        String json = String.valueOf(response);
        List<RasaResponse> responses = null;
        try {
            responses = this.gson.fromJson(json, RasaResponse.TYPE);
        } catch (JsonSyntaxException e) {
            // let the null check below handle the error
        }

        if (responses == null) {
           dispatchError(dispatcher, "invalid server response: " + json);
           return new ArrayList<>();
        }
        return responses;
    }

    private void dispatchResponse(DialogueDispatcher dispatcher,
                                  RasaResponse response) {
        DialogueEvent.Type eventType = null;
        ConversationState.Builder state = new ConversationState.Builder();

        if (response.text != null) {
            String id = String.valueOf(response.text.hashCode());
            Prompt prompt = new Prompt.Builder(id, response.text).build();
            state.withPrompt(prompt);
            eventType = DialogueEvent.Type.PROMPT;
        } else if (response.image != null) {
            String id = String.valueOf(response.image.hashCode());
            Prompt prompt = new Prompt.Builder(id, response.image).build();
            state
                  .withPrompt(prompt)
                  .withAction(IMAGE_ACTION, new HashMap<>());
            eventType = DialogueEvent.Type.ACTION;
        }

        if (eventType != null) {
            DialogueEvent event = new DialogueEvent(eventType, state.build());
            dispatcher.dispatch(event);
        }
    }

    @Override
    public void completeTurn(
          boolean success,
          ConversationData conversationData,
          DialogueDispatcher eventDispatcher) {

    }

    /**
     * Wrapper class used to deserialize JSON responses from Rasa Open Source.
     */
    private static class RasaResponse {
        private static final Type TYPE =
              new TypeToken<ArrayList<RasaResponse>>() {}.getType();
        private String text;
        private String image;
    }
}
