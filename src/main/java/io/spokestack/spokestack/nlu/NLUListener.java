package io.spokestack.spokestack.nlu;

/**
 * A simple interface for objects that wish to receive results from the NLU
 * subsystem.
 */
public interface NLUListener {

    /**
     * A notification that an NLU result is available. This can represent a
     * successful classification or an error.
     *
     * @param result The NLU result.
     */
    void resultReceived(NLUResult result);
}
