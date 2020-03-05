package io.spokestack.spokestack.nlu.tensorflow;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

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
        private Map<String, Slot> slotIndex;

        Intent(String slotName, Slot[] slotMetas) {
            this.name = slotName;
            this.slots = slotMetas;
        }

        public String getName() {
            return name;
        }

        public Slot getSlot(String slotName) {
            if (slotIndex == null) {
                slotIndex = new HashMap<>();
                for (Slot slot : slots) {
                    slotIndex.put(slot.name, slot);
                }
            }
            return slotIndex.get(slotName);
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
        private String facets;
        private Map<String, Object> parsedFacets;

        Slot(String slotName, String slotType, String slotFacets) {
            this.name = slotName;
            this.type = slotType;
            this.facets = slotFacets;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public Map<String, Object> getFacets() {
            if (parsedFacets == null) {
                Gson gson = new Gson();
                if (facets != null) {
                    Type jsonType =
                          new TypeToken<Map<String, Object>>() { }.getType();
                    parsedFacets = gson.fromJson(facets, jsonType);
                } else {
                    parsedFacets = new HashMap<>();
                }
            }
            return parsedFacets;
        }
    }
}
