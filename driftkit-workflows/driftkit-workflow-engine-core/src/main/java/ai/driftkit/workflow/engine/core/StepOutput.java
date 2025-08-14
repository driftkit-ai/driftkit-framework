package ai.driftkit.workflow.engine.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper for step output that preserves type information.
 * Values are stored as JSON and deserialized lazily with proper type restoration.
 */
@Slf4j
@Data
@NoArgsConstructor
public class StepOutput {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private JsonNode valueJson;
    private String className;
    
    @JsonIgnore
    private transient Object cachedValue;
    
    @JsonIgnore
    private transient Class<?> cachedClass;
    
    /**
     * Creates a StepOutput from a value, automatically capturing its type.
     */
    public static StepOutput of(Object value) {
        if (value == null) {
            return new StepOutput();
        }
        
        StepOutput output = new StepOutput();
        output.className = value.getClass().getName();
        
        try {
            output.valueJson = OBJECT_MAPPER.valueToTree(value);
            output.cachedValue = value;
            output.cachedClass = value.getClass();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize step output", e);
        }
        
        return output;
    }
    
    /**
     * Gets the value, deserializing from JSON if needed.
     * The value is cached after first deserialization.
     */
    public Object getValue() {
        if (cachedValue != null) {
            return cachedValue;
        }
        
        if (valueJson == null || className == null) {
            return null;
        }
        
        try {
            Class<?> clazz = getActualClass();
            cachedValue = OBJECT_MAPPER.treeToValue(valueJson, clazz);
            return cachedValue;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to deserialize step output of type " + className, e
            );
        }
    }
    
    /**
     * Gets the value as the specified type.
     * 
     * @param type The expected type
     * @return The value cast to the type
     * @throws ClassCastException if the value cannot be cast
     */
    @SuppressWarnings("unchecked")
    public <T> T getValueAs(Class<T> type) {
        Object value = getValue();
        
        if (value == null) {
            return null;
        }
        
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        
        throw new ClassCastException(
            "Cannot cast step output of type " + value.getClass().getName() + 
            " to " + type.getName()
        );
    }
    
    /**
     * Checks if this output is compatible with the given type.
     */
    public boolean isCompatibleWith(Class<?> type) {
        if (type == null) {
            return false;
        }
        
        Class<?> actualClass = getActualClass();
        if (actualClass == null) {
            return false;
        }
        
        return type.isAssignableFrom(actualClass);
    }
    
    /**
     * Gets the actual class of the stored value.
     * Lazily loads and caches the class from className if needed.
     */
    @JsonIgnore
    public Class<?> getActualClass() {
        // If we have the cached class, return it
        if (cachedClass != null) {
            return cachedClass;
        }
        
        // Try to load from className
        if (className != null) {
            try {
                cachedClass = Class.forName(className);
                return cachedClass;
            } catch (ClassNotFoundException e) {
                log.error("Cannot load class: {}", className, e);
                throw new IllegalStateException("Cannot load class: " + className, e);
            }
        }
        
        return null;
    }
    
    /**
     * Checks if this output has a value.
     */
    public boolean hasValue() {
        return valueJson != null && className != null;
    }
}