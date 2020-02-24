package io.spokestack.spokestack.nlu.tensorflow.parsers;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IntegerParserTest {

    @Test
    public void testParse() {
        IntegerParser parser = new IntegerParser();
        HashMap<String, Object> metadata = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () ->
              parser.parse(metadata, "invalid"));
        assertThrows(IllegalArgumentException.class, () ->
              parser.parse(metadata, ""));

        // out of range
        metadata.put("range", Arrays.asList(1D, 11D));
        assertThrows(IllegalArgumentException.class, () ->
              parser.parse(metadata, "twelve"));
        assertThrows(IllegalArgumentException.class, () ->
              parser.parse(metadata, "12"));
        assertThrows(IllegalArgumentException.class, () ->
              parser.parse(metadata, "1000000000000"));

        assertEquals(10, parser.parse(metadata, "Ten"));
        assertEquals(10, parser.parse(metadata, "10"));

        metadata.put("range", Arrays.asList(1D, 10000000D));

        assertEquals(22, parser.parse(metadata, "twenty-two"));
        assertEquals(22, parser.parse(metadata, "twenty two"));

        assertEquals(1000000, parser.parse(metadata, "one million"));
        assertEquals(1000000, parser.parse(metadata, "1000000"));

        String text = "three thousand two hundred forty two";
        assertEquals(3242, parser.parse(metadata, text));
        text = "three thousand two hundred forty second";
        assertEquals(3242, parser.parse(metadata, text));

        assertEquals(13, parser.parse(metadata, "thirteen"));
        assertEquals(13, parser.parse(metadata, "thirteenth"));
    }
}