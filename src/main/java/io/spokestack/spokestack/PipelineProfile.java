package io.spokestack.spokestack;

/**
 * A pipeline profile encapsulates a series of configuration values tuned for
 * a specific task to make building a {@link SpeechPipeline} more convenient.
 *
 * <p>
 * Profiles are not authoritative; they act just like calling a series of
 * methods on a {@link SpeechPipeline.Builder}, and any configuration
 * properties they set can be overridden by subsequent calls.
 * </p>
 *
 * <p>
 * Pipeline profiles must not require arguments in their constructors.
 * </p>
 */
public interface PipelineProfile {

    /**
     * Apply this profile to the pipeline builder.
     *
     * @param builder The builder to which the profile should be applied.
     * @return The modified pipeline builder.
     */
    SpeechPipeline.Builder apply(SpeechPipeline.Builder builder);
}
