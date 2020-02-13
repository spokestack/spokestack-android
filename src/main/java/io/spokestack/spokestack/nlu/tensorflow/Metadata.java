package io.spokestack.spokestack.nlu.tensorflow;

/**
 * A schema class used for metadata parsed from a JSON file that accompanies a
 * TensorFlow Lite NLU model. The metadata contains the information necessary to
 * translate the model's raw outputs into actionable intent and slot data.
 */
final class Metadata {

    private Intent[] intents;
    private String[] tags;

    /**
     * @return the metadata for all intents associated with this model.
     */
    public Intent[] getIntents() {
        return intents;
    }

    /**
     * @return all tags associated with this model.
     */
    public String[] getTags() {
        return tags;
    }

    /**
     * An intent definition. In model metadata, an intent consists of a name
     * and a collection of slots recognized along with the intent.
     */
    static class Intent {
        private String name;
        private Slot[] slots;

        Intent(String slotName, Slot[] slotMetas) {
            this.name = slotName;
            this.slots = slotMetas;
        }

        public String getName() {
            return name;
        }

        public Slot[] getSlots() {
            return slots;
        }
    }

    /**
     * A slot definition. In model metadata, a slot consists of a name, a type,
     * and additional clarifying information that varies according to slot
     * type.
     *
     * <p>
     * For example, a slot with the "selset" type will list individual
     * {@code selections} that contain aliases for each value. An "integer"
     * slot may contain information about the range of values it should
     * consider valid.
     * </p>
     */
    static class Slot {
        private String name;
        private String type;

        // optional slot contents for various slot types
        private Selection[] selections;

        Slot(String slotName, String slotType) {
            this.name = slotName;
            this.type = slotType;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * A single selection used by a selset to normalize concepts with varying
     * surface forms into a single term.
     */
    static class Selection {
        private String name;
        private String[] aliases;
    }
}
