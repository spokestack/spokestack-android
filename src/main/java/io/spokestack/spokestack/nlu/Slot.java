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
    private final Object value;

    /**
     * Create a new slot.
     *
     * @param n   The slot's name.
     * @param val The slot's value.
     */
    public Slot(String n, Object val) {
        this.name = n;
        this.value = val;
    }

    /**
     * @return The slot's name.
     */
    public String getName() {
        return name;
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
              && getValue().equals(slot.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getValue());
    }

    @Override
    public String toString() {
        return "Slot{"
              + "name='" + name + '\''
              + ", value=" + value
              + '}';
    }

    /**
     * @return The slot's value.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Create a typed slot by parsing its value using type information from the
     * model's metadata.
     *
     * @param type  The type of slot to be parsed.
     * @param name  The slot's name.
     * @param value The string version of the slot's value to be parsed.
     * @return A slot with a value parsed using the supplied type information.
     */
    public static Slot parse(String type, String name, String value) {
        switch (type) {
            case "integer":
                return new Slot(name, Integer.parseInt(value));
            case "entity":
                return new Slot(name, value);
            default:
                throw new IllegalArgumentException(
                      "Unknown slot type: " + type);
        }
    }
}

