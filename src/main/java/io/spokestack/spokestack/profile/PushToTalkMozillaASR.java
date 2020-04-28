package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that relies on manual pipeline activation,
 * using Mozilla's on-device DeepSpeech for ASR.
 *
 * <p>
 * DeepSpeech requires extra configuration, which must be added to the
 * pipeline build process separately from this profile:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>mozilla-model-path</b> (string): path to deepspeech tflite file
 *   </li>
 *   <li>
 *      <b>locale</b> (string): language code for speech recognition
 *   </li>
 * </ul>
 *
 * @see io.spokestack.spokestack.mozilla.MozillaSpeechRecognizer
 */
public class PushToTalkMozillaASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        return builder
              .setInputClass(
                    "io.spokestack.spokestack.android.MicrophoneInput")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AutomaticGainControl")
              .setProperty("agc-compression-gain-db", 15)
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityDetector")
              .addStageClass("io.spokestack.spokestack.ActivationTimeout")
              .addStageClass(
                    "io.spokestack.spokestack.mozilla.MozillaSpeechRecognizer");
    }
}
