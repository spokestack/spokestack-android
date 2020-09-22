package io.spokestack.spokestack.nlu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlotTest {

    @Test
    public void equals() {
        Slot one = new Slot("test", "1", 1);
        assertNotEquals(one, new Object());
        assertEquals(one, one);

        Slot alsoOne = new Slot("test", "1", 1);
        assertEquals(one, alsoOne);

        Slot two = new Slot("test", "1", 2);
        assertNotEquals(one, two);
        two = new Slot("test", "2", 1);
        assertNotEquals(one, two);
        two = new Slot("two", "1", 1);
        assertNotEquals(one, two);
    }

    @Test
    public void testHashCode() {
        Slot one = new Slot("test", "1", 1);
        Slot alsoOne = new Slot("test", "1", 1);
        assertEquals(one.hashCode(), alsoOne.hashCode());

        // original value does not matter for hashcode
        alsoOne = new Slot("test", "2", 1);
        assertEquals(one.hashCode(), alsoOne.hashCode());

        Slot two = new Slot("test", "2", 2);
        assertNotEquals(one.hashCode(), two.hashCode());
    }

    @Test
    void testToString() {
        Slot one = new Slot("test", "1", 1);
        String expected = "Slot{name=\"test\", type=null, rawValue=1, value=1}";
        assertEquals(expected, one.toString());
    }
}