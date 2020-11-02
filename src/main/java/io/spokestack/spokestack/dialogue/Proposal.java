package io.spokestack.spokestack.dialogue;

/**
 * A proposal indicates the intended interpretation of a user's "yes" or "no"
 * response in the current conversational context by rewriting the relevant
 * intent into a more appropriate one.
 */
public class Proposal {

    private String accept;
    private String reject;

    /**
     * Constructs a new proposal. This constructor is designed to be used by
     * Gson deserialization.
     */
    public Proposal() {
    }

    /**
     * @return The intent, if any, mapped to an affirmative response.
     */
    public String getAccept() {
        return accept;
    }

    /**
     * @return The intent, if any, mapped to a negative response.
     */
    public String getReject() {
        return reject;
    }

    @Override
    public String toString() {
        return "Proposal{"
              + "accept='" + accept + '\''
              + ", reject='" + reject + '\''
              + '}';
    }
}
