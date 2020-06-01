package io.spokestack.spokestack.nlu.tensorflow;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
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
    private final Intent[] intents;
    private final String[] tags;

    Metadata(Intent[] intentArr, String[] tagArr) {
        this.intents = intentArr;
        this.tags = tagArr;
    }

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
     * An intent definition. In model metadata, an intent consists of a name and
     * a collection of slots recognized along with the intent.
     */
    static class Intent {
        @SerializedName("implicit_slots")
        private final Slot[] implicitSlots;
        private final String name;
        private final Slot[] slots;

        Intent(String intentName, Slot[] slotMetas, Slot[] implicitSlotMetas) {
            this.name = intentName;
            this.slots = slotMetas;
            this.implicitSlots = implicitSlotMetas;
        }

        public String getName() {
            return name;
        }

        public Slot[] getImplicitSlots() {
            if (implicitSlots != null) {
                return implicitSlots;
            }
            return new Slot[0];
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
     * For example, a slot with the "selset" type will list individual {@code
     * selections} that contain aliases for each value. An "integer" slot may
     * contain information about the range of values it should consider valid.
     * </p>
     *
     * <p>
     * The {@code value} field will only be present for implicit slots.
     * </p>
     */
    static class Slot {
        @SerializedName("capture_name")
        private final String captureName;
        private final String name;
        private final String type;
        private final Object value;
        private final String facets;
        private Map<String, Object> parsedFacets;

        Slot(String sName,
             String sCaptureName,
             String sType,
             String sFacets,
             Object sValue) {
            this.name = sName;
            this.captureName = sCaptureName;
            this.type = sType;
            this.value = sValue;
            this.facets = sFacets;
        }

        public String getCaptureName() {
            if (captureName != null) {
                return captureName;
            }
            return name;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public Object getValue() {
            return value;
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
