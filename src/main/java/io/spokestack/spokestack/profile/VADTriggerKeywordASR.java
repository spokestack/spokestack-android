package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * A speech pipeline profile that uses voice activity detection to activate
 * ASR based on keyword detection. In other words, any speech will cause the
 * pipeline to activate, and {@code KeywordRecognizer} acts as the ASR
 * component, only transcribing utterances contained in its vocabulary.
 *
 * <p>
 * The activation period ends either:
 *
 * <ul>
 *   <li>
 *      when speech stops, as determined by {@code VoiceActivityDetector}'s
 *      configuration, or
 *   </li>
 *   <li>
 *      when the activation period exceeds the timeout configured in
 *      {@code ActivationTimeout}
 *   </li>
 * </ul>
 *
 * <p>
 * If no valid keyword is detected during the activation period, a timeout
 * event will be dispatched.
 * </p>
 *
 * <p>
 * The components in this profile process speech entirely on the local device;
 * no cloud components are involved.
 * </p>
 *
 * <p>
 * The keyword detector requires extra configuration, which must be
 * added to the pipeline build process separately from this profile:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>keyword-classes</b> (string): comma-separated ordered
 *      list of class names for the keywords; the name corresponding to the
 *      most likely class will be returned in the transcript field when the
 *      recognition event is raised
 *   </li>
 *   <li>
 *      <b>keyword-filter-path</b> (string): file system path to the
 *      "filter" Tensorflow-Lite model
 *   </li>
 *   <li>
 *      <b>keyword-encode-path</b> (string): file system path to the
 *      "encode" Tensorflow-Lite model
 *   </li>
 *   <li>
 *      <b>keyword-detect-path</b> (string): file system path to the
 *      "detect" Tensorflow-Lite model
 *   </li>
 * </ul>
 *
 * @see io.spokestack.spokestack.asr.KeywordRecognizer
 */
public class VADTriggerKeywordASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        List<String> stages = new ArrayList<>();
        stages.add("io.spokestack.spokestack.webrtc.AutomaticGainControl");
        stages.add("io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor");
        stages.add("io.spokestack.spokestack.webrtc.VoiceActivityDetector");
        stages.add("io.spokestack.spokestack.webrtc.VoiceActivityTrigger");
        stages.add("io.spokestack.spokestack.ActivationTimeout");
        stages.add("io.spokestack.spokestack.asr.KeywordRecognizer");

        return builder
              .setInputClass("io.spokestack.spokestack.android.MicrophoneInput")
              .setStageClasses(stages);
    }
}
