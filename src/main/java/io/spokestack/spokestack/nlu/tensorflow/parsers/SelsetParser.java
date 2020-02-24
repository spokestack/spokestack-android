package io.spokestack.spokestack.nlu.tensorflow.parsers;

import io.spokestack.spokestack.nlu.tensorflow.SlotParser;

import java.util.List;
import java.util.Map;

/**
 * A parser that resolves selset values to their canonical names.
 */
public class SelsetParser implements SlotParser {

    /**
     * Create a new selset parser.
     */
    public SelsetParser() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object parse(Map<String, Object> metadata,
                        String rawValue) {
        List<Object> selections = null;
        try {
            selections = (List<Object>) metadata.get("selections");
        } catch (ClassCastException e) {
            // do nothing; catch with the null check below
        }

        if (selections == null) {
            throw new IllegalArgumentException("invalid selset facets");
        }

        String normalized = rawValue.toLowerCase();
        for (Object selection : selections) {
            Map<String, Object> selMap = (Map<String, Object>) selection;
            String name = String.valueOf(selMap.get("name"));
            if (name.toLowerCase().equals(normalized)) {
                return name;
            }
            List<String> aliases = (List<String>) selMap.get("aliases");
            for (String alias : aliases) {
                if (alias.toLowerCase().equals(normalized)) {
                    return name;
                }
            }
        }
        throw new IllegalArgumentException("invalid alias: " + rawValue);
    }
}
