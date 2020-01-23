package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that uses TensorFlow Lite for wakeword detection
 * and Azure Speech Service for ASR. Properties related to
 * signal processing are tuned for the "Spokestack" wakeword.
 *
 * <p>
 * Wakeword detection requires configuration to locate the models used for
 * classification; these properties must be set elsewhere:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>wake-filter-path</b> (string, required): file system path to the
 *      "filter" Tensorflow-Lite model, which is used to calculate a mel
 *      spectrogram frame from the linear STFT; its inputs should be shaped
 *      [fft-width], and its outputs [mel-width]
 *   </li>
 *   <li>
 *      <b>wake-encode-path</b> (string, required): file system path to the
 *      "encode" Tensorflow-Lite model, which is used to perform each
 *      autoregressive step over the mel frames; its inputs should be shaped
 *      [mel-length, mel-width], and its outputs [encode-width], with an
 *      additional state input/output shaped [state-width]
 *   </li>
 *   <li>
 *      <b>wake-detect-path</b> (string, required): file system path to the
 *      "detect" Tensorflow-Lite model; its inputs shoudld be shaped
 *      [encode-length, encode-width], and its outputs [1]
 *   </li>
 * </ul>
 *
 * <p>
 * Azure Speech Service requires the following properties:
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
 * @see io.spokestack.spokestack.wakeword.WakewordTrigger
 */
public class TFWakewordAzureASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        return builder
              .setInputClass(
                    "io.spokestack.spokestack.android.MicrophoneInput")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor")
              .setProperty("ans-policy", "aggressive")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AutomaticGainControl")
              .setProperty("agc-target-level-dbfs", 3)
              .setProperty("agc-compression-gain-db", 15)
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityDetector")
              .setProperty("vad-mode", "very-aggressive")
              .setProperty("vad-fall-delay", 800)
              .addStageClass(
                    "io.spokestack.spokestack.wakeword.WakewordTrigger")
              .setProperty("wake-threshold", 0.9)
              .setProperty("pre-emphasis", 0.97)
              .addStageClass("io.spokestack.spokestack.ActivationTimeout")
              .setProperty("active-min", 2000)
              .addStageClass(
                    "io.spokestack.spokestack.microsoft.AzureSpeechRecognizer");
    }
}
