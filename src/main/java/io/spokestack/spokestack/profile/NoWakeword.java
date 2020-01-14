package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that does not perform wakeword detection. This
 * profile is simply a shortcut for using the device microphone for input and
 * performs no other configuration.
 *
 * @see io.spokestack.spokestack.android.MicrophoneInput
 */
public class NoWakeword implements PipelineProfile {

    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        return builder.setInputClass(
              "io.spokestack.spokestack.android.MicrophoneInput");
    }
}
