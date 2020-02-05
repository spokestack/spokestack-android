package io.spokestack.spokestack.nlu;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * A simple interface for components that provide intent classification and slot
 * recognition, either on-device or via a network request.
 */
public interface NLUService {

    /**
     * Classifies a user utterance into an intent asynchronously.
     *
     * @param utterance The user utterance to be classified.
     * @param context   Any contextual information that should be sent along
     *                  with the utterance to assist classification.
     * @return A {@code Future} representing the completed classification task.
     */
    Future<NLUResult> classify(String utterance, Map<String, Object> context);
}
