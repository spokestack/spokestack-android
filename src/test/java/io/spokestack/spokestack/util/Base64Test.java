package io.spokestack.spokestack.util;


import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class Base64Test {

    @Test
    public void testEncode() {
        byte[] bytes = "test string10".getBytes(StandardCharsets.UTF_8);
        assertEquals("dGVzdCBzdHJpbmcxMA==", Base64.encode(bytes));
        bytes = "test string".getBytes(StandardCharsets.UTF_8);
        assertEquals("dGVzdCBzdHJpbmc=", Base64.encode(bytes));
        bytes = "test stri".getBytes(StandardCharsets.UTF_8);
        assertEquals("dGVzdCBzdHJp", Base64.encode(bytes));
    }

}