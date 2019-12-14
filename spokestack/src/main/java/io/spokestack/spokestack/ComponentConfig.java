package io.spokestack.spokestack;

import java.util.HashMap;
import java.util.Map;

/**
 * Spokestack component configuration
 *
 * <p>
 * This class allows generic configuration properties to pass through
 * various builder abstractions down to implementations. Configuration values
 * may be primitive types and are registered in a global namespace. The
 * pipeline performs the following primitive type conversions (input on rows).
 * </p>
 *
 * <table summary="type conversions">
 *  <tr><td></td><td>integer</td><td>double</td><td>string</td></tr>
 *  <tr><td>integer</td><td>*</td><td>*</td><td>*</td></tr>
 *  <tr><td>double</td> <td>*</td><td>*</td><td>*</td></tr>
 *  <tr><td>string</td> <td>*</td><td>*</td><td>*</td></tr>
 * </table>
 */
public final class ComponentConfig {
    private final Map<String, Object> params;

    /** initializes a default configuration instance. */
    public ComponentConfig() {
        this(new HashMap<>());
    }

    /**
     * attaches an existing configuration map.
     * @param value configuration map to attach
    */
    public ComponentConfig(Map<String, Object> value) {
        this.params = value;
    }

    /** @return the attached configuration map */
    public Map<String, Object> getParams() {
        return this.params;
    }

    /**
     * determines whether a configuration key is present.
     * @param key key to look up
     * @return true if the key was found, false otherwise
     */
    public boolean containsKey(String key) {
        return this.params.containsKey(key);
    }

    /**
     * fetches a string value, coercing if needed.
     * @param key          key to look up
     * @param defaultValue value to return if not found
     * @return the string configuration value if found, defaultValue otherwise
     */
    public String getString(String key, String defaultValue) {
        return this.params.containsKey(key)
            ? getString(key)
            : defaultValue;
    }

    /**
     * fetches a string value. coercing if needed.
     * @param key key to look up
     * @return the string configuration value
     */
    public String getString(String key) {
        if (!this.params.containsKey(key))
            throw new IllegalArgumentException(key);

        Object o = this.params.get(key);
        if (o == null)
            return "";
        if (o instanceof String)
            return (String) o;
        return o.toString();
    }

    /**
     * fetches an integer value, coercing if needed.
     * @param key          key to look up
     * @param defaultValue value to return if not found
     * @return the integer configuration value if found, defaultValue otherwise
     */
    public int getInteger(String key, Integer defaultValue) {
        return this.params.containsKey(key)
            ? getInteger(key)
            : defaultValue;
    }

    /**
     * fetches an integer value. coercing if needed.
     * @param key key to look up
     * @return the integer configuration value
     */
    public int getInteger(String key) {
        if (!this.params.containsKey(key))
            throw new IllegalArgumentException(key);

        Object o = this.params.get(key);
        if (o instanceof Double)
            return (int) (double) o;
        if (o instanceof String)
            return Integer.parseInt((String) o);
        return (int) o;
    }

    /**
     * fetches an string value, coercing if needed.
     * @param key          key to look up
     * @param defaultValue value to return if not found
     * @return the double configuration value if found, defaultValue otherwise
     */
    public double getDouble(String key, Double defaultValue) {
        return this.params.containsKey(key)
            ? getDouble(key)
            : defaultValue;
    }

    /**
     * fetches a double value. coercing if needed.
     * @param key key to look up
     * @return the double configuration value
     */
    public double getDouble(String key) {
        if (!this.params.containsKey(key))
            throw new IllegalArgumentException(key);

        Object o = this.params.get(key);
        if (o instanceof Integer)
            return (double) (int) o;
        if (o instanceof String)
            return Double.parseDouble((String) o);
        return (double) o;
    }

    /**
     * writes a configuration value.
     * @param key   key to put
     * @param value value to store for the key
     * @return this
     */
    public ComponentConfig put(String key, Object value) {
        this.params.put(key, value);
        return this;
    }
}
