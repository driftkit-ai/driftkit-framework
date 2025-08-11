package ai.driftkit.workflow.engine.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable container for workflow execution context with thread-safe operations.
 * Provides access to the current run state and results from previously executed steps.
 * 
 * <p>This class maintains two separate storage areas:
 * <ul>
 *   <li>stepOutputs - Internal storage for workflow engine step results</li>
 *   <li>customData - User-defined data storage for workflow-specific values</li>
 * </ul>
 * All operations are thread-safe using ConcurrentHashMap.</p>
 */
@Slf4j
@Getter
public class WorkflowContext {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final String runId;
    private final Object triggerData;
    private final ConcurrentHashMap<String, Object> stepOutputs;
    private final ConcurrentHashMap<String, Object> customData;
    private final Object workflowInstance;
    
    /**
     * Well-known keys for special context values.
     */
    public static final class Keys {
        public static final String FINAL_RESULT = "__final__";
        public static final String USER_INPUT = "__user_input__";
        public static final String USER_INPUT_TYPE = "__user_input_type__";
        public static final String RESUMED_STEP_INPUT = "__resumed_step_input__";
        
        // Chat-specific keys
        public static final String CHAT_ID = "__chat_id__";
        public static final String USER_ID = "__user_id__";
        public static final String STEP_INVOCATION_COUNTS = "__step_invocation_counts__";
        
        // Async-specific keys
        public static final String ASYNC_FUTURE = "_future";

        private Keys() {} // prevent instantiation
    }
    
    /**
     * Creates a new WorkflowContext with the provided parameters.
     */
    private WorkflowContext(String runId, Object triggerData, 
                          Map<String, Object> stepOutputs,
                          Map<String, Object> customData,
                          Object workflowInstance) {
        this.runId = (runId == null || runId.isBlank()) ? UUID.randomUUID().toString() : runId;
        this.triggerData = triggerData;
        this.stepOutputs = new ConcurrentHashMap<>();
        if (stepOutputs != null) {
            this.stepOutputs.putAll(stepOutputs);
        }
        this.customData = new ConcurrentHashMap<>();
        if (customData != null) {
            this.customData.putAll(customData);
        }
        this.workflowInstance = workflowInstance;
    }
    
    /**
     * Creates a new WorkflowContext for a fresh workflow run.
     * 
     * @param triggerData The initial data that triggered the workflow
     * @return A new WorkflowContext with a generated runId
     */
    public static WorkflowContext newRun(Object triggerData) {
        return new WorkflowContext(
            UUID.randomUUID().toString(),
            triggerData,
            null,
            null,
            null
        );
    }
    
    /**
     * Creates a new WorkflowContext for a fresh workflow run with a workflow instance.
     * 
     * @param triggerData The initial data that triggered the workflow
     * @param workflowInstance The workflow instance
     * @return A new WorkflowContext with a generated runId
     */
    public static WorkflowContext newRun(Object triggerData, Object workflowInstance) {
        return new WorkflowContext(
            UUID.randomUUID().toString(),
            triggerData,
            null,
            null,
            workflowInstance
        );
    }
    
    /**
     * Internal factory method for creating context with existing data.
     */
    static WorkflowContext fromExisting(String runId, Object triggerData,
                                      Map<String, Object> stepOutputs,
                                      Map<String, Object> customData,
                                      Object workflowInstance) {
        return new WorkflowContext(runId, triggerData, stepOutputs, customData, workflowInstance);
    }
    
    /**
     * Retrieves the output of a previously executed step.
     * 
     * @param stepId The ID of the step whose output to retrieve
     * @param type The expected type of the output
     * @param <T> The type parameter
     * @return The step output cast to the requested type
     * @throws NoSuchElementException if the step output doesn't exist
     * @throws ClassCastException if the output cannot be cast to the requested type
     */
    @SuppressWarnings("unchecked")
    public <T> T getStepResult(String stepId, Class<T> type) {
        Object result = stepOutputs.get(stepId);
        if (result == null) {
            throw new NoSuchElementException("No output found for step: " + stepId);
        }
        
        return convertToType(result, type, "step output");
    }
    
    /**
     * Retrieves the output of a previously executed step, returning a default value if not found.
     * 
     * @param stepId The ID of the step whose output to retrieve
     * @param type The expected type of the output
     * @param defaultValue The default value to return if the step output doesn't exist
     * @param <T> The type parameter
     * @return The step output cast to the requested type, or the default value
     */
    public <T> T getStepResultOrDefault(String stepId, Class<T> type, T defaultValue) {
        try {
            return getStepResult(stepId, type);
        } catch (NoSuchElementException e) {
            return defaultValue;
        }
    }
    
    /**
     * Checks if a step has produced output.
     * 
     * @param stepId The ID of the step to check
     * @return true if the step has output, false otherwise
     */
    public boolean hasStepResult(String stepId) {
        return stepOutputs.containsKey(stepId);
    }
    
    /**
     * Sets the output for a step (internal use by workflow engine).
     * 
     * @param stepId The ID of the step that produced the output
     * @param output The output produced by the step
     */
    public void setStepOutput(String stepId, Object output) {
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        
        if (output == null) {
            stepOutputs.remove(stepId);
        } else {
            stepOutputs.put(stepId, output);
        }
        
        log.trace("Set step output for '{}': {}", stepId, 
            output != null ? output.getClass().getSimpleName() : "null");
    }
    
    /**
     * Sets multiple step outputs at once (internal use by workflow engine).
     * 
     * @param outputs Map of step IDs to their outputs
     */
    public void setStepOutputs(Map<String, Object> outputs) {
        if (outputs == null) {
            return;
        }
        
        outputs.forEach(this::setStepOutput);
    }
    
    /**
     * Sets a custom value in the context (for user data).
     * 
     * @param key The key for the custom value
     * @param value The value to store
     */
    public void setContextValue(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        
        if (value == null) {
            customData.remove(key);
        } else {
            customData.put(key, value);
        }
        
        log.trace("Set context value for '{}': {}", key,
            value != null ? value.getClass().getSimpleName() : "null");
    }
    
    /**
     * Gets a custom value from the context.
     * 
     * @param key The key for the custom value
     * @param type The expected type of the value
     * @param <T> The type parameter
     * @return The value cast to the requested type, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key, Class<T> type) {
        Object value = customData.get(key);
        if (value == null) {
            return null;
        }
        
        return convertToType(value, type, "context value");
    }
    
    /**
     * Gets a custom value from the context with a default.
     * 
     * @param key The key for the custom value
     * @param type The expected type of the value
     * @param defaultValue The default value if not found
     * @param <T> The type parameter
     * @return The value cast to the requested type, or the default value
     */
    public <T> T getContextValueOrDefault(String key, Class<T> type, T defaultValue) {
        T value = getContextValue(key, type);
        return value != null ? value : defaultValue;
    }
    
    // Helper methods for common types
    
    /**
     * Gets a string value from custom data.
     */
    public String getString(String key) {
        return getContextValue(key, String.class);
    }
    
    /**
     * Gets a string value with default.
     */
    public String getStringOrDefault(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets an integer value from custom data.
     */
    public Integer getInt(String key) {
        return getContextValue(key, Integer.class);
    }
    
    /**
     * Gets an integer value with default.
     */
    public Integer getIntOrDefault(String key, Integer defaultValue) {
        Integer value = getInt(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets a long value from custom data.
     */
    public Long getLong(String key) {
        return getContextValue(key, Long.class);
    }
    
    /**
     * Gets a long value with default.
     */
    public Long getLongOrDefault(String key, Long defaultValue) {
        Long value = getLong(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets a boolean value from custom data.
     */
    public Boolean getBoolean(String key) {
        return getContextValue(key, Boolean.class);
    }
    
    /**
     * Gets a boolean value with default.
     */
    public Boolean getBooleanOrDefault(String key, Boolean defaultValue) {
        Boolean value = getBoolean(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets a double value from custom data.
     */
    public Double getDouble(String key) {
        return getContextValue(key, Double.class);
    }
    
    /**
     * Gets a double value with default.
     */
    public Double getDoubleOrDefault(String key, Double defaultValue) {
        Double value = getDouble(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Gets a list from custom data.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key, Class<T> elementType) {
        Object value = customData.get(key);
        if (value instanceof List) {
            return (List<T>) value;
        }
        return null;
    }
    
    /**
     * Gets a map from custom data.
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType) {
        Object value = customData.get(key);
        if (value instanceof Map) {
            return (Map<K, V>) value;
        }
        return null;
    }
    
    /**
     * Gets the trigger data cast to a specific type.
     * 
     * @param type The expected type of the trigger data
     * @param <T> The type parameter
     * @return The trigger data cast to the requested type
     * @throws ClassCastException if the trigger data cannot be cast to the requested type
     */
    public <T> T getTriggerData(Class<T> type) {
        if (triggerData == null) {
            return null;
        }
        
        return convertToType(triggerData, type, "trigger data");
    }
    
    /**
     * Returns the number of step outputs currently stored.
     * 
     * @return The number of step outputs
     */
    public int getStepCount() {
        return stepOutputs.size();
    }
    
    /**
     * Returns the number of custom data entries.
     * 
     * @return The number of custom data entries
     */
    public int getCustomDataCount() {
        return customData.size();
    }
    
    /**
     * Creates a minimal string representation for logging.
     * 
     * @return A string representation of the context
     */
    @Override
    public String toString() {
        return "WorkflowContext{" +
               "runId='" + runId + '\'' +
               ", stepCount=" + stepOutputs.size() +
               ", customDataCount=" + customData.size() +
               ", steps=" + stepOutputs.keySet() +
               '}';
    }
    
    /**
     * Creates a new WorkflowContext with step outputs for backward compatibility.
     * Used internally during migration.
     */
    WorkflowContext withStepOutputs(Map<String, Object> outputs) {
        WorkflowContext newContext = WorkflowContext.fromExisting(
            this.runId, this.triggerData, outputs, this.customData, this.workflowInstance);
        return newContext;
    }
    
    /**
     * Converts a value to the requested type, handling JSON deserialization.
     * 
     * @param value The value to convert
     * @param type The target type
     * @param description Description for error messages (e.g., "step output", "trigger data")
     * @param <T> The type parameter
     * @return The value converted to the requested type
     * @throws ClassCastException if conversion fails
     */
    @SuppressWarnings("unchecked")
    private static <T> T convertToType(Object value, Class<T> type, String description) {
        if (value == null) {
            return null;
        }
        
        // If already the correct type, return directly
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        
        // Handle JSON deserialization
        try {
            // If value is a JsonNode, use treeToValue for proper conversion
            if (value instanceof JsonNode) {
                return OBJECT_MAPPER.treeToValue((JsonNode) value, type);
            }
            // If value is a Map (from JSON deserialization), convert it
            else if (value instanceof Map) {
                return OBJECT_MAPPER.convertValue(value, type);
            }
            // For other types, try direct cast
            else {
                return type.cast(value);
            }
        } catch (Exception e) {
            throw new ClassCastException(
                "Cannot convert " + description + " to " + type.getName() + ": " + e.getMessage()
            );
        }
    }
}