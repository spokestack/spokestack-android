package io.spokestack.spokestack.nlu.tensorflow;

import java.util.Map;

/**
 * An interface for components capable of parsing raw string slot values into
 * concrete objects.
 */
public interface SlotParser {

    /**
     * Parse a raw string value from an utterance into a typed value for a
     * concrete slot.
     *
     * <p>
     * Implementers should throw an exception if the parsed value is invalid
     * according to the supplied metadata.
     * </p>
     *
     * @param metadata A map representing metadata for the particular slot being
     *                 parsed.
     * @param rawValue The string from a user utterance that represents a slot
     *                 value.
     * @return The slot represented by {@code value}.
     * @throws Exception if there is an error during parsing.
     */
    Object parse(Map<String, Object> metadata, String rawValue)
          throws Exception;
}
