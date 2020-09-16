package io.spokestack.spokestack;

/**
 * A functional interface used to edit an ASR transcript before it is passed to
 * the NLU subsystem for classification.
 */
public interface TranscriptEditor {

    /**
     * Edit the ASR transcript to correct errors or perform other normalization
     * before NLU classification occurs.
     * @param transcript The transcript received from the ASR component.
     * @return An edited transcript.
     */
    String editTranscript(String transcript);
}
