package io.spokestack.spokestack.nlu.tensorflow.parsers;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DigitsParserTest {

    @Test
    public void parseErrors() {
        DigitsParser parser = new DigitsParser();
        HashMap<String, Object> metadata = new HashMap<>();

        assertThrows(IllegalArgumentException.class, () -> {
            parser.parse(metadata, "invalid");
        });

        metadata.put("count", 10D);
        assertThrows(IllegalArgumentException.class, () -> {
            parser.parse(metadata, "123456789");
        });

        metadata.put("count", 1D);
        assertThrows(IllegalArgumentException.class, () -> {
            parser.parse(metadata, "thirty");
        });
    }

    @Test
    public void testParse() {
        DigitsParser parser = new DigitsParser();
        HashMap<String, Object> metadata = new HashMap<>();

        // mod 20
        assertEquals("1", parser.parse(metadata, " one"));
        assertEquals("2", parser.parse(metadata, "two "));
        assertEquals("3", parser.parse(metadata, " three "));
        assertEquals("4", parser.parse(metadata, "  four  "));
        assertEquals("5", parser.parse(metadata, "five"));
        assertEquals("6", parser.parse(metadata, "six"));
        assertEquals("7", parser.parse(metadata, "seven"));
        assertEquals("8", parser.parse(metadata, "eight"));
        assertEquals("9", parser.parse(metadata, "nine"));
        assertEquals("10", parser.parse(metadata, "ten"));
        assertEquals("11", parser.parse(metadata, "eleven"));
        assertEquals("12", parser.parse(metadata, "twelve"));
        assertEquals("13", parser.parse(metadata, "thirteen"));
        assertEquals("14", parser.parse(metadata, "fourteen"));
        assertEquals("15", parser.parse(metadata, "fifteen"));
        assertEquals("16", parser.parse(metadata, "sixteen"));
        assertEquals("17", parser.parse(metadata, "seventeen"));
        assertEquals("18", parser.parse(metadata, "eighteen"));
        assertEquals("19", parser.parse(metadata, "nineteen"));

        // div 10
        assertEquals("20", parser.parse(metadata, "twenty"));
        assertEquals("3001", parser.parse(metadata, "thirty  zero   one"));
        assertEquals("41", parser.parse(metadata, "forty one"));
        assertEquals("52", parser.parse(metadata, "fifty-two"));
        assertEquals("68", parser.parse(metadata, "sixty - eight"));
        assertEquals("7010", parser.parse(metadata, "seventy ten"));

        // hundreds
        assertEquals("100", parser.parse(metadata, "one hundred"));
        assertEquals("18003352211", parser.parse(metadata,
              "one eight hundred three three five twenty-two eleven"));
        assertEquals("18003352211", parser.parse(metadata, "18003352211"));

        // thousands
        assertEquals("2000", parser.parse(metadata, "two thousand"));
        assertEquals("18003354000", parser.parse(metadata,
              "one eight hundred three thirty-five four thousand"));

        // homophones
        assertEquals("0000", parser.parse(metadata, "zero oh oh owe"));
        assertEquals("11", parser.parse(metadata, "one won"));
        assertEquals("222", parser.parse(metadata, "two too to"));
        assertEquals("444", parser.parse(metadata, "four for fore"));
        assertEquals("666", parser.parse(metadata, "six sicks sics"));
        assertEquals("88", parser.parse(metadata, "eight ate"));
        assertEquals("1010", parser.parse(metadata, "ten tin"));

        // mixed
        assertEquals("012345667789", parser.parse(metadata,
              "oh one 23 for five 66 seventy-seven ate 9"));
    }
}