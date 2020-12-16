package io.spokestack.spokestack.nlu;

import io.spokestack.spokestack.util.AsyncResult;

/**
 * A simple interface for components that provide intent classification and slot
 * recognition, either on-device or via a network request.
 *
 * <p>
 * To participate in Spokestack's {@link NLUManager}, an NLUService must have a
 * constructor that accepts instances of {@link io.spokestack.spokestack.SpeechConfig}
 * and {@link NLUContext}.
 * </p>
 */
public interface NLUService extends AutoCloseable {

    /**
     * Classifies a user utterance. Classification should be performed on a
     * background thread, but the use of an {@link AsyncResult} allows the
     * caller to either block while waiting for a result or register a callback
     * to be executed when the result is available.
     *
     * @param utterance The user utterance to be classified.
     * @param context   The current NLU context, containing request metadata and
     *                  the ability to fire trace events.
     * @return An {@link AsyncResult} representing the result of the
     * classification task.
     * @see AsyncResult#registerCallback(io.spokestack.spokestack.util.Callback)
     */
    AsyncResult<NLUResult> classify(String utterance, NLUContext context);
}
