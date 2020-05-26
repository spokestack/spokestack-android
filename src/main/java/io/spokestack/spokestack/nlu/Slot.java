package io.spokestack.spokestack.nlu;

import java.util.Objects;

/**
 * A slot extracted during intent classification.
 *
 * <p>
 * Depending on the NLU service used, slots may be typed; if present, the type
 * of each slot can be accessed by calling {@code getClass()} on the slot's
 * value.
 * </p>
 */
public final class Slot {
    private final String name;
    private final String rawValue;
    private final Object value;

    /**
     * Create a new slot.
     *
     * @param n   The slot's name.
     * @param original The slot's original string value.
     * @param parsed The slot's parsed value.
     */
    public Slot(String n, String original, Object parsed) {
        this.name = n;
        this.rawValue = original;
        this.value = parsed;
    }

    /**
     * @return The slot's name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The slot's original value in the user utterance, before being
     * processed by any parsers.
     */
    public String getRawValue() {
        return rawValue;
    }

    /**
     * @return The slot's parsed value.
     */
    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Slot slot = (Slot) o;
        return getName().equals(slot.getName())
              && nullOrEqual(getRawValue(), slot.getRawValue())
              && nullOrEqual(getValue(), slot.getValue());
    }

    private boolean nullOrEqual(Object obj1, Object obj2) {
        return (obj1 == null && obj2 == null)
            || (obj1 != null && obj1.equals(obj2));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getValue());
    }

    @Override
    public String toString() {
        return "Slot{"
              + "name=\"" + name
              + "\", rawValue=" + rawValue
              + ", value=" + value
              + '}';
    }
}
