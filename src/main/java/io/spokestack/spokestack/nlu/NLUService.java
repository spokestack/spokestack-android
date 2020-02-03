package io.spokestack.spokestack.nlu;

import java.util.ArrayList;
import java.util.List;

/**
 * A base class for components that provide intent classification and slot
 * recognition, either on-device or via a network request.
 *
 * <p>
 * Intent classification is designed to be an asynchronous process; in order
 * to receive results as they are available, add a listener to the NLU
 * service.
 * </p>
 */
public abstract class NLUService {

    /**
     * Listeners that receive results from this component.
     */
    private List<NLUListener> listeners = new ArrayList<>();

    /**
     * Add an NLU listener to receive results from this component.
     *
     * @param listener The listener to add.
     */
    public void addListener(NLUListener listener) {
        this.listeners.add(listener);
    }

    /**
     * Classifies a user utterance into an intent.
     *
     * Implementations should ensure that {@link #dispatch(NLUResult)} is
     * called when a result is available so that listeners are notified.
     *
     * @param request The user request to be classified.
     */
    public abstract void classify(NLURequest request);

    /**
     * Dispatch an NLU result to all registered listeners.
     *
     * @param result The result to dispatch.
     */
    public void dispatch(NLUResult result) {
        for (NLUListener listener : listeners) {
            listener.resultReceived(result);
        }
    }
}
