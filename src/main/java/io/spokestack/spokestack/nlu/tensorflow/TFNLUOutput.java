package io.spokestack.spokestack.nlu.tensorflow;

import io.spokestack.spokestack.nlu.NLUContext;
import io.spokestack.spokestack.util.Tuple;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * An internal class to contain the business logic for turning raw model outputs
 * into usable intents/slots.
 */
final class TFNLUOutput {

    TFNLUOutput() {
    }

    /**
     * Extract the intent from the model's output tensor.
     *
     * @param metadata The metadata for the current NLU model.
     * @param output   The output tensor containing the intent prediction.
     * @return A tuple consisting of the intent from the model's output tensor
     * and the model's posterior probability (confidence value) for that
     * prediction.
     */
    public Tuple<Metadata.Intent, Float> getIntent(
          Metadata metadata, ByteBuffer output) {
        Metadata.Intent[] intents = metadata.getIntents();
        Tuple<Integer, Float> prediction = bufferArgMax(output, intents.length);
        return new Tuple<>(intents[prediction.first()], prediction.second());
    }

    /**
     * Extract the slot values captured for a specific utterance from the
     * model's slot tag output tensor.
     *
     * @param context  The context used to communicate trace events.
     * @param metadata The metadata for the current NLU model.
     * @param encoded  The original encoded input, used to determine string
     *                 values for model output.
     * @param output   The output tensor containing slot tag predictions.
     * @return A map of slot name to raw string values.
     */
    public Map<String, String> getSlots(
          NLUContext context,
          Metadata metadata,
          EncodedTokens encoded,
          ByteBuffer output) {
        int numTokens = encoded.getIds().size();
        String[] tagLabels = getLabels(metadata, output, numTokens);
        context.traceDebug("Tag labels: %s", Arrays.toString(tagLabels));
        Map<Integer, Integer> slotLocations = new HashMap<>();
        Integer curSlotStart = null;
        for (int i = 0; i < tagLabels.length; i++) {
            String label = tagLabels[i];
            if (label.equals("o")) {
                if (curSlotStart != null) {
                    curSlotStart = null;
                }
            } else {
                if (label.startsWith("b")) {
                    curSlotStart = i;
                }

                // add both b_ and i_ tagged tokens to the current slot
                if (curSlotStart != null) {
                    slotLocations.put(curSlotStart, i + 1);
                }
            }
        }

        Map<String, String> slots = new HashMap<>();
        for (Map.Entry<Integer, Integer> slotRange : slotLocations.entrySet()) {
            String value = encoded.decodeRange(slotRange.getKey(),
                  slotRange.getValue(), true);
            String slotName = tagLabels[slotRange.getKey()].substring(2);
            slots.put(slotName, value);
        }
        return slots;
    }

    String[] getLabels(Metadata metadata, ByteBuffer output,
                       int numTokens) {
        int numTags = metadata.getTags().length;
        String[] labels = new String[numTokens];
        for (int i = 0; i < labels.length; i++) {
            Tuple<Integer, Float> labelled = bufferArgMax(output, numTags);
            labels[i] = metadata.getTags()[labelled.first()];
        }
        return labels;
    }

    private Tuple<Integer, Float> bufferArgMax(ByteBuffer buffer, int n) {
        float[] posteriors = new float[n];
        for (int i = 0; i < n; i++) {
            posteriors[i] = buffer.getFloat();
        }
        return argMax(posteriors);
    }

    private Tuple<Integer, Float> argMax(float[] values) {
        int maxIndex = 0;
        float maxValue = values[0];

        for (int i = 1; i < values.length; i++) {
            float curVal = values[i];
            if (curVal > maxValue) {
                maxIndex = i;
                maxValue = curVal;
            }
        }
        return new Tuple<>(maxIndex, maxValue);
    }
}
