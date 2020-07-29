package io.spokestack.spokestack.nlu;

import java.util.Objects;

/**
 * A slot extracted during intent classification.
 *
 * <p>
 * Some NLU services allow user-specified types. If present, the
 * user-specified type can be accessed via the {@code type} field, and the
 * runtime type can be accessed by calling {@code getClass()} on the slot's
 * {@code value}.
 * </p>
 */
public final class Slot {
    private final String name;
    private final String type;
    private final String rawValue;
    private final Object value;

    /**
     * Create a new slot.
     *
     * @param n        The slot's name.
     * @param original The slot's original string value.
     * @param parsed   The slot's parsed value.
     */
    public Slot(String n, String original, Object parsed) {
        this(n, null, original, parsed);
    }

    /**
     * Create a new slot with a user-specified type.
     *
     * @param n        The slot's name.
     * @param userType The slot's type.
     * @param original The slot's original string value.
     * @param parsed   The slot's parsed value.
     */
    public Slot(String n, String userType, String original, Object parsed) {
        this.name = n;
        this.type = userType;
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
     * @return The slot's type.
     */
    public String getType() {
        return type;
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
              && nullOrEqual(getType(), slot.getType())
              && nullOrEqual(getRawValue(), slot.getRawValue())
              && nullOrEqual(getValue(), slot.getValue());
    }

    private boolean nullOrEqual(Object obj1, Object obj2) {
        return (obj1 == null && obj2 == null)
            || (obj1 != null && obj1.equals(obj2));
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getType(), getValue());
    }

    @Override
    public String toString() {
        return "Slot{"
              + "name=\"" + name
              + "\", type=" + type
              + ", rawValue=" + rawValue
              + ", value=" + value
              + '}';
    }
}
