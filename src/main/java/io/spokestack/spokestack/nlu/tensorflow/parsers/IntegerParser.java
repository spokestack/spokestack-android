package io.spokestack.spokestack.nlu.tensorflow.parsers;

import io.spokestack.spokestack.nlu.tensorflow.SlotParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * This parser converts string representations of integers into integers. These
 * strings may be digits or cardinal or ordinal number names. Strict hyphenation
 * of two-digit numbers is optional.
 *
 * <p>
 * Only positive values are recognized.
 * </p>
 *
 * <p>
 * A "range" property is expected in the slot's metadata; this property
 * represents a half-open interval of (start, end] for the range of values the
 * parser should accept. Regardless of the end of the interval, the maximum
 * value that will be parsed is Java's {@link Integer#MAX_VALUE}; values greater
 * than this will result in an exception.
 * </p>
 */
public final class IntegerParser implements SlotParser {
    private static final Map<String, Integer> WORD_TO_NUM = new HashMap<>();
    private static final Map<String, Integer> MULTIPLIERS = new HashMap<>();
    private static final Pattern DIGIT_SPLIT_RE = Pattern.compile("[-,()\\s]");

    /**
     * Create a new integer parser.
     */
    public IntegerParser() {
        initMaps();
    }

    private void initMaps() {
        WORD_TO_NUM.put("oh", 0);
        WORD_TO_NUM.put("owe", 0);
        WORD_TO_NUM.put("zero", 0);
        WORD_TO_NUM.put("won", 1);
        WORD_TO_NUM.put("one", 1);
        WORD_TO_NUM.put("first", 1);
        WORD_TO_NUM.put("to", 2);
        WORD_TO_NUM.put("too", 2);
        WORD_TO_NUM.put("two", 2);
        WORD_TO_NUM.put("second", 2);
        WORD_TO_NUM.put("three", 3);
        WORD_TO_NUM.put("third", 3);
        WORD_TO_NUM.put("for", 4);
        WORD_TO_NUM.put("fore", 4);
        WORD_TO_NUM.put("four", 4);
        WORD_TO_NUM.put("five", 5);
        WORD_TO_NUM.put("fif", 5);
        WORD_TO_NUM.put("sicks", 6);
        WORD_TO_NUM.put("sics", 6);
        WORD_TO_NUM.put("six", 6);
        WORD_TO_NUM.put("seven", 7);
        WORD_TO_NUM.put("ate", 8);
        WORD_TO_NUM.put("eight", 8);
        WORD_TO_NUM.put("eighth", 8);
        WORD_TO_NUM.put("nine", 9);
        WORD_TO_NUM.put("ninth", 9);
        WORD_TO_NUM.put("tin", 10);
        WORD_TO_NUM.put("ten", 10);
        WORD_TO_NUM.put("eleven", 11);
        WORD_TO_NUM.put("twelve", 12);
        WORD_TO_NUM.put("twelf", 12);
        WORD_TO_NUM.put("thirteen", 13);
        WORD_TO_NUM.put("fourteen", 14);
        WORD_TO_NUM.put("fifteen", 15);
        WORD_TO_NUM.put("sixteen", 16);
        WORD_TO_NUM.put("seventeen", 17);
        WORD_TO_NUM.put("eighteen", 18);
        WORD_TO_NUM.put("nineteen", 19);
        WORD_TO_NUM.put("twenty", 20);
        WORD_TO_NUM.put("twentie", 20);
        WORD_TO_NUM.put("thirty", 30);
        WORD_TO_NUM.put("thirtie", 30);
        WORD_TO_NUM.put("forty", 40);
        WORD_TO_NUM.put("fortie", 40);
        WORD_TO_NUM.put("fifty", 50);
        WORD_TO_NUM.put("fiftie", 50);
        WORD_TO_NUM.put("sixty", 60);
        WORD_TO_NUM.put("sixtie", 60);
        WORD_TO_NUM.put("seventy", 70);
        WORD_TO_NUM.put("seventie", 70);
        WORD_TO_NUM.put("eighty", 80);
        WORD_TO_NUM.put("eightie", 80);
        WORD_TO_NUM.put("ninety", 90);
        WORD_TO_NUM.put("ninetie", 90);

        MULTIPLIERS.put("hundred", 100);
        MULTIPLIERS.put("thousand", 1000);
        MULTIPLIERS.put("million", 1000000);
        MULTIPLIERS.put("billion", 1000000000);
        WORD_TO_NUM.putAll(MULTIPLIERS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object parse(Map<String, Object> metadata,
                        String rawValue) {
        List<Double> range = null;
        Object rawRange = metadata.get("range");
        if (rawRange != null) {
            range = (List<Double>) rawRange;
        }
        String normalized = rawValue.toLowerCase().trim();
        List<Integer> parsedInts = new ArrayList<>();
        String[] tokens = DIGIT_SPLIT_RE.split(normalized);
        for (String token : tokens) {
            try {
                int parsed = Integer.parseInt(token);
                parsedInts.add(parsed);
            } catch (NumberFormatException nfe) {
                parseReduce(token, parsedInts);
            }
        }
        int result = sum(parsedInts);
        if (isInRange(result, range)) {
            return result;
        }
        throw new IllegalArgumentException("number out of range: " + result);
    }

    private void parseReduce(String numStr, List<Integer> soFar) {
        String toParse = numStr;
        if (toParse.endsWith("th")) {
            toParse = toParse.substring(0, toParse.length() - 2);
        }
        if (!WORD_TO_NUM.containsKey(toParse)) {
            throw new IllegalArgumentException("Invalid integer: " + toParse);
        }

        if (MULTIPLIERS.containsKey(toParse)) {
            List<Integer> sum = collapse(MULTIPLIERS.get(toParse), soFar);
            soFar.clear();
            soFar.addAll(sum);
        } else {
            soFar.add(WORD_TO_NUM.get(toParse));
        }
    }

    private List<Integer> collapse(int multiplier, List<Integer> soFar) {
        List<Integer> collapsed = new ArrayList<>();
        int sum = 0;
        for (Integer num : soFar) {
            if (num > multiplier) {
                collapsed.add(num);
            } else {
                sum += num;
            }
        }
        sum = (sum > 0) ? sum : 1;
        collapsed.add(sum * multiplier);
        return collapsed;
    }

    private int sum(List<Integer> parsed) {
        int sum = 0;
        for (Integer num : parsed) {
            sum += num;
        }
        return sum;
    }

    private boolean isInRange(int val, List<Double> range) {
        return range == null
              || (val > 0
              && range.get(0) < range.get(1)
              && val >= range.get(0)
              && val < range.get(1));
    }
}
