package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * A speech pipeline profile that relies on manual pipeline activation,
 * using Android's {@code SpeechRecognizer} API for ASR.
 *
 * <p>
 * Using Android's built-in ASR requires that an Android {@code Context} object
 * be attached to the speech pipeline using it. This must be done separately
 * from profile application, using
 * {@link SpeechPipeline.Builder#setAndroidContext(android.content.Context)}.
 * </p>
 *
 * @see io.spokestack.spokestack.android.AndroidSpeechRecognizer
 */
public class PushToTalkAndroidASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        List<String> stages = new ArrayList<>();
        stages.add("io.spokestack.spokestack.android.AndroidSpeechRecognizer");
        return builder
              .setInputClass("io.spokestack.spokestack.android.NoInput")
              .setStageClasses(stages);
    }
}
