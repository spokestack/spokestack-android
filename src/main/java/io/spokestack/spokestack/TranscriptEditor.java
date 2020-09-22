package io.spokestack.spokestack;

/**
 * A functional interface used to edit an ASR transcript before it is passed to
 * the NLU subsystem for classification.
 *
 * <p>
 * This can be used to alter ASR results that frequently contain a spelling for
 * a homophone that's incorrect for the domain; for example, an app used to
 * summon a genie whose ASR transcripts tend to contain "Jen" instead of
 * "djinn".
 * </p>
 */
public interface TranscriptEditor {

    /**
     * Edit the ASR transcript to correct errors or perform other normalization
     * before NLU classification occurs.
     *
     * @param transcript The transcript received from the ASR component.
     * @return An edited transcript.
     */
    String editTranscript(String transcript);
}
