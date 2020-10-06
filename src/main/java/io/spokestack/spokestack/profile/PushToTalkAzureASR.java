package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * A speech pipeline profile that relies on manual pipeline activation, using
 * Azure Speech Service for ASR.
 *
 * <p>
 * Azure Speech Service requires extra configuration, which must be added to the
 * pipeline build process separately from this profile:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>azure-api-key</b> (string): Azure API key
 *   </li>
 *   <li>
 *      <b>azure-region</b> (string): service region for Azure key
 *   </li>
 * </ul>
 *
 * @see io.spokestack.spokestack.microsoft.AzureSpeechRecognizer
 */
public class PushToTalkAzureASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        List<String> stages = new ArrayList<>();
        stages.add("io.spokestack.spokestack.webrtc.AutomaticGainControl");
        stages.add("io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor");
        stages.add("io.spokestack.spokestack.webrtc.VoiceActivityDetector");
        stages.add("io.spokestack.spokestack.ActivationTimeout");
        stages.add("io.spokestack.spokestack.microsoft.AzureSpeechRecognizer");

        return builder
              .setInputClass("io.spokestack.spokestack.android.MicrophoneInput")
              .setStageClasses(stages);
    }
}
