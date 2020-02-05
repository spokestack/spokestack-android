package io.spokestack.spokestack.nlu;

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
    private final String slotName;
    private final Object slotValue;

    /**
     * Create a new slot.
     *
     * @param name  The slot's name.
     * @param value The slot's value.
     */
    public Slot(String name, Object value) {
        this.slotName = name;
        this.slotValue = value;
    }

    /**
     * @return The slot's name.
     */
    public String getName() {
        return slotName;
    }

    /**
     * @return The slot's value.
     */
    public Object getValue() {
        return slotValue;
    }
}

