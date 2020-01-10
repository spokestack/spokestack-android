package io.spokestack.spokestack;

import java.util.*;

import org.junit.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

public class SpeechConfigTest {
    @Test
    public void testConstruction() {
        SpeechConfig config;

        config = new SpeechConfig();
        assertFalse(config.containsKey("test"));
        assertTrue(config.getParams().isEmpty());

        Map<String, Object> params = new HashMap<>();
        params.put("test", "value");
        config = new SpeechConfig(params);
        assertTrue(config.containsKey("test"));
        assertEquals("value", config.getString("test"));
    }

    @Test
    public void testString() {
        final SpeechConfig config = new SpeechConfig();

        // default value
        assertEquals("default", config.getString("string", "default"));
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { config.getString("string"); }
        });

        // null value
        config.put("string", null);
        assertEquals("", config.getString("string", "default"));
        assertEquals("", config.getString("string"));

        // string value
        config.put("string", "test");
        assertEquals("test", config.getString("string", "default"));
        assertEquals("test", config.getString("string"));

        // non-string value
        config.put("string", 1);
        assertEquals("1", config.getString("string", "default"));
        assertEquals("1", config.getString("string"));
    }

    @Test
    public void testInteger() {
        final SpeechConfig config = new SpeechConfig();

        // default value
        assertEquals(42, config.getInteger("integer", 42));
        assertThrows(IllegalArgumentException.class, new Executable() {
                public void execute() { config.getInteger("double"); }
        });

        // integer value
        config.put("integer", 1);
        assertEquals(1, config.getInteger("integer", 42));
        assertEquals(1, config.getInteger("integer"));

        // double value
        config.put("integer", 3.14);
        assertEquals(3, config.getInteger("integer", 42));
        assertEquals(3, config.getInteger("integer"));

        // string value
        config.put("integer", "2");
        assertEquals(2, config.getInteger("integer", 42));
        assertEquals(2, config.getInteger("integer"));
    }

    @Test
    public void testDouble() {
        final SpeechConfig config = new SpeechConfig();

        // default value
        assertEquals(42.0, config.getDouble("double", 42.0));
        assertThrows(IllegalArgumentException.class, new Executable() {
            public void execute() { config.getDouble("double"); }
        });

        // double value
        config.put("double", 3.14);
        assertEquals(3.14, config.getDouble("double", 1.0));
        assertEquals(3.14, config.getDouble("double"));

        // integer value
        config.put("double", 3);
        assertEquals(3.0, config.getDouble("double", 1.0));
        assertEquals(3.0, config.getDouble("double"));

        // string value
        config.put("double", "2.72");
        assertEquals(2.72, config.getDouble("double", 1.0));
        assertEquals(2.72, config.getDouble("double"));
    }
}
