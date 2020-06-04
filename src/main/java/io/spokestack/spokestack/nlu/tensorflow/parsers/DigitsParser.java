package io.spokestack.spokestack.nlu.tensorflow.parsers;

import io.spokestack.spokestack.nlu.tensorflow.SlotParser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This parser converts a string representation of a sequence of digits into the
 * corresponding sequence of digits. Strings may include english cardinal
 * representations of numbers, as well as some simple homophones.
 *
 * <p>
 * Only positive values are recognized.
 * </p>
 *
 * <p>
 * These may include hyphenated or unhyphenated numbers from twenty through
 * ninety-nine. Unhyphenated numbers are automatically joined.
 * </p>
 *
 * <p>
 * Allowing unhyphenated double-digit numbers creates an ambiguity that limits
 * some representations, for example "sixty five thousand" could be parsed as
 * either 65000 or 605000. For this reason, 2- and 3-digit numbers are
 * restricted to even hundreds/thousands. In this model, the above number would
 * always be parsed as "65000" -- in other words, "sixty five thousand one"
 * would be parsed as "650001". This limitation should be acceptable for most
 * multi-digit requirements (telephone numbers, social security numbers, etc.)
 * </p>
 */
public final class DigitsParser implements SlotParser {
    private static final Map<String, Integer> ENG_ZERO = new HashMap<>();
    private static final Map<String, Integer> ENG_MOD10 = new HashMap<>();
    private static final Map<String, Integer> ENG_MOD20 = new HashMap<>();
    private static final Map<String, Integer> ENG_DIV10 = new HashMap<>();
    private static final Map<String, Integer> ENG_EXP10 = new HashMap<>();
    private static final Pattern DIGIT_SPLIT_RE = Pattern.compile("[-,()\\s]+");

    /**
     * Create a new digit parser.
     */
    public DigitsParser() {
        initMaps();
    }

    private void initMaps() {
        ENG_ZERO.put("zero", 0);
        ENG_ZERO.put("oh", 0);
        ENG_ZERO.put("owe", 0);

        ENG_MOD10.put("one", 1);
        ENG_MOD10.put("won", 1);
        ENG_MOD10.put("two", 2);
        ENG_MOD10.put("to", 2);
        ENG_MOD10.put("too", 2);
        ENG_MOD10.put("three", 3);
        ENG_MOD10.put("four", 4);
        ENG_MOD10.put("for", 4);
        ENG_MOD10.put("fore", 4);
        ENG_MOD10.put("five", 5);
        ENG_MOD10.put("six", 6);
        ENG_MOD10.put("sicks", 6);
        ENG_MOD10.put("sics", 6);
        ENG_MOD10.put("seven", 7);
        ENG_MOD10.put("eight", 8);
        ENG_MOD10.put("ate", 8);
        ENG_MOD10.put("nine", 9);

        ENG_MOD20.put("ten", 10);
        ENG_MOD20.put("tin", 10);
        ENG_MOD20.put("eleven", 11);
        ENG_MOD20.put("twelve", 12);
        ENG_MOD20.put("thirteen", 13);
        ENG_MOD20.put("fourteen", 14);
        ENG_MOD20.put("fifteen", 15);
        ENG_MOD20.put("sixteen", 16);
        ENG_MOD20.put("seventeen", 17);
        ENG_MOD20.put("eighteen", 18);
        ENG_MOD20.put("nineteen", 19);

        ENG_DIV10.put("twenty", 2);
        ENG_DIV10.put("thirty", 3);
        ENG_DIV10.put("forty", 4);
        ENG_DIV10.put("fifty", 5);
        ENG_DIV10.put("sixty", 6);
        ENG_DIV10.put("seventy", 7);
        ENG_DIV10.put("eighty", 8);
        ENG_DIV10.put("ninety", 9);

        ENG_EXP10.put("hundred", 2);
        ENG_EXP10.put("thousand", 3);
    }

    @Override
    public Object parse(Map<String, Object> metadata,
                        String rawValue) {
        Object count = metadata.get("count");
        Double numDigits = (count == null) ? null : (double) count;
        String normalized = rawValue.toLowerCase();
        StringBuilder resultBuilder = new StringBuilder();
        String[] tokens = DIGIT_SPLIT_RE.split(normalized);
        for (int i = 0; i < tokens.length; i++) {
            String next = (i < tokens.length - 1) ? tokens[i + 1] : null;
            resultBuilder.append(parseSingle(tokens[i], next));
        }
        if ((numDigits == null && resultBuilder.length() > 0)
            || (numDigits != null && resultBuilder.length() == numDigits)) {
            return resultBuilder.toString();
        }
        return null;
    }

    private String parseSingle(String numStr, String next) {
        if (ENG_ZERO.containsKey(numStr)) {
            return String.valueOf(ENG_ZERO.get(numStr));
        } else if (ENG_MOD10.containsKey(numStr)) {
            return String.valueOf(ENG_MOD10.get(numStr));
        } else if (ENG_MOD20.containsKey(numStr)) {
            return String.valueOf(ENG_MOD20.get(numStr));
        } else if (ENG_DIV10.containsKey(numStr)
              && ENG_MOD10.containsKey(next)) {
            return String.valueOf(ENG_DIV10.get(numStr));
        } else if (ENG_DIV10.containsKey(numStr)) {
            return String.valueOf(ENG_DIV10.get(numStr) * 10);
        } else if (ENG_EXP10.containsKey(numStr)) {
            int exp = ENG_EXP10.get(numStr);
            return String.format("%0" + exp + "d", 0);
        } else {
            try {
                return String.valueOf(Long.parseLong(numStr));
            } catch (NumberFormatException nfe) {
                return "";
            }
        }
    }
}
