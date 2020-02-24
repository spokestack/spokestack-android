package io.spokestack.spokestack.nlu.tensorflow.parsers;

import io.spokestack.spokestack.nlu.tensorflow.SlotParser;

import java.util.Map;

/**
 * A parser that does not alter string slot values recognized by the model.
 */
public class IdentityParser implements SlotParser {

    /**
     * Create a new identity parser.
     */
    public IdentityParser() {
    }

    @Override
    public Object parse(Map<String, Object> metadata,
                        String rawValue) {
        return rawValue;
    }
}
