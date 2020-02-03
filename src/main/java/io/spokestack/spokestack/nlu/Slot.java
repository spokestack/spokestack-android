package io.spokestack.spokestack.nlu;

/**
 * A slot extracted during intent classification.
 *
 * <p>
 * Depending on the NLU service used, slots may be typed; if present, the type
 * of each slot can be used to cast its raw value to the appropriate class
 * after NLU has finished.
 * </p>
 */
public final class Slot {
    private final String slotName;
    private final Class<?> slotType;
    private final Object slotValue;

    /**
     * Create a new untyped slot.
     * @param name The slot's name.
     * @param value The slot's value.
     */
    public Slot(String name, Object value) {
        this(name, null, value);
    }

    /**
     * Create a new typed slot.
     * @param name The slot's name.
     * @param type The slot's type.
     * @param value The slot's value.
     */
    public Slot(String name, Class<?> type, Object value) {
        this.slotName = name;
        this.slotType = type;
        this.slotValue = value;
    }

    /**
     * @return The slot's name.
     */
    public String getName() {
        return slotName;
    }

    /**
     * @return The slot's type.
     */
    public Class<?> getType() {
        return slotType;
    }

    /**
     * @return The slot's value.
     */
    public Object getValue() {
        return slotValue;
    }
}

