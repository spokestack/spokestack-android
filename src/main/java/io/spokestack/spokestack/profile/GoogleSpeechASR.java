package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that uses Google Speech for ASR.
 *
 * <p>
 * Since the configuration properties used by Google Speech are app-specific,
 * those must be set outside this class.
 * </p>
 *
 * @see io.spokestack.spokestack.google.GoogleSpeechRecognizer
 */
public class GoogleSpeechASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        return builder.addStageClass(
              "io.spokestack.spokestack.google.GoogleSpeechRecognizer");
    }
}
