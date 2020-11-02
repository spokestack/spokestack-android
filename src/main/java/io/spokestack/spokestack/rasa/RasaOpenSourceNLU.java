package io.spokestack.spokestack.rasa;

import com.google.gson.Gson;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUContext;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.NLUService;
import io.spokestack.spokestack.util.AsyncResult;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * An NLU component that submits utterances to a Rasa Open Source server to
 * retrieve responses.
 *
 * <p>
 * Since the input for a Rasa Open Source request is a user utterance, this
 * component occupies the position in Spokestack of an NLU, but a Rasa webhook
 * response includes results from the dialogue model, leaving out NLU results.
 * Therefore, this component returns a constant value as the intent and
 * includes the webhook response as a list inside the result context under a
 * {@code "responses"} key.
 * </p>
 *
 * <p>
 * This component is designed to be used with a {@link io.spokestack.spokestack.dialogue.DialogueManager}
 * built with a {@link RasaDialoguePolicy} policy, which expects results in
 * the format just described and converts the Rasa responses into TTS
 * prompts or app events as appropriate.
 * </p>
 *
 * <p>
 * This component supports the following configuration properties:
 * </p>
 * <ul>
 *   <li>
 *      <b>rasa-oss-url</b> (string, required): URL to the Rasa Open Source
 *      server. This component is designed to use Rasa's REST channel.
 *   </li>
 *   <li>
 *      <b>rasa-sender-id</b> (string, optional): Sender ID for Rasa requests.
 *      Defaults to "spokestack-android".
 *   </li>
 *   <li>
 *      <b>rasa-oss-token</b> (string, optional): Token to use in requests to
 *      the Rasa Open Source server. See
 *      <a href="https://rasa.com/docs/rasa/http-api#token-based-auth">Rasa's
 *      documentation</a> for more details.
 *   </li>
 *   <li>
 *      <b>rasa-oss-jwt</b> (string, optional): A full JWT header (including
 *      the "Bearer " prefix) to use in requests to the Rasa Open Source server.
 *      See <a href="https://rasa.com/docs/rasa/http-api#jwt-based-auth">Rasa's
 *      documentation</a> for more details.
 *   </li>
 * </ul>
 */
public final class RasaOpenSourceNLU implements NLUService {

    /**
     * The designated intent produced by this component since Rasa Open Source
     * responses do not include classification results.
     */
    public static final String RASA_INTENT = "rasa.core";

    /**
     * The designated key for response messages from Rasa Open Source in an
     * {@link NLUResult} produced by this component.
     */
    public static final String RESPONSE_KEY = "responses";

    private static final String DEFAULT_SENDER = "spokestack-android";
    private static final MediaType APPLICATION_JSON =
          MediaType.parse("application/json");

    private final ExecutorService executor =
          Executors.newSingleThreadExecutor();

    private final String coreUrl;
    private final String token;
    private final String jwt;
    private final String senderId;
    private final NLUContext context;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * Create a new Rasa Open Source NLU component.
     *
     * @param config     configuration properties
     * @param nluContext The NLU context used to dispatch trace events and
     *                   errors.
     */
    public RasaOpenSourceNLU(SpeechConfig config, NLUContext nluContext) {
        this(config,
              nluContext,
              new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
        );
    }

    RasaOpenSourceNLU(SpeechConfig config,
                      NLUContext nluContext,
                      OkHttpClient client) {
        this.coreUrl = config.getString("rasa-oss-url");
        this.token = config.getString("rasa-oss-token", null);
        this.jwt = config.getString("rasa-oss-jwt", null);
        this.senderId = config.getString("rasa-sender-id", DEFAULT_SENDER);
        this.context = nluContext;
        this.gson = new Gson();
        this.httpClient = client;
    }

    @Override
    public AsyncResult<NLUResult> classify(String utterance,
                                           NLUContext nluContext) {

        AsyncResult<NLUResult> asyncResult = new AsyncResult<>(
              () -> requestClassification(utterance)
        );
        this.executor.submit(asyncResult);
        return asyncResult;
    }

    private NLUResult requestClassification(String utterance) {
        NLUResult.Builder resultBuilder = new NLUResult.Builder(utterance);
        try {
            Request request = buildRequest(utterance);
            Response response = httpClient.newCall(request).execute();
            String body = "<no body>";
            ResponseBody responseBody = response.body();

            if (responseBody != null) {
                body = responseBody.string();
            }

            if (response.isSuccessful()) {
                Map<String, Object> rasaMeta = new HashMap<>();
                rasaMeta.put(RESPONSE_KEY, body);
                resultBuilder
                      .withContext(rasaMeta)
                      .withIntent(RASA_INTENT)
                      .withConfidence(1.0f);
            } else {
                this.context.traceError("Rasa HTTP error (%d): %s",
                      response.code(), body);
            }
        } catch (IOException e) {
            resultBuilder.withError(e);
        }
        return resultBuilder.build();
    }

    private Request buildRequest(String utterance) {
        Map<String, Object> body = new HashMap<>();
        body.put("sender", this.senderId);
        body.put("message", utterance);

        if (this.token != null) {
            body.put("token", this.token);
        }

        String fullBodyJson = gson.toJson(body);
        RequestBody postBody =
              RequestBody.create(fullBodyJson, APPLICATION_JSON);

        Request.Builder builder = new Request.Builder();

        if (this.jwt != null) {
            builder = builder.addHeader("Authorization", this.jwt);
        }

        return builder
              .url(this.coreUrl)
              .post(postBody)
              .build();
    }

    @Override
    public void close() throws Exception {
        this.executor.shutdownNow();
    }
}
