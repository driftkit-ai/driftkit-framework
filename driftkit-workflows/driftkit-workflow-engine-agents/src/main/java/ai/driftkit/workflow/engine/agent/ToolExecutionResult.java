package ai.driftkit.workflow.engine.agent;

import lombok.Builder;
import lombok.Data;

/**
 * Result of tool execution with typed data.
 */
@Data
@Builder
public class ToolExecutionResult {
    private final String toolName;
    private final Object result;
    private final Class<?> resultType;
    private final boolean success;
    private final String error;
    
    // Convenience method to get typed result
    public <T> T getTypedResult(Class<T> type) {
        if (result == null) {
            return null;
        }
        if (type.isInstance(result)) {
            return type.cast(result);
        }
        throw new ClassCastException("Cannot cast result of type " + result.getClass().getName() + " to " + type.getName());
    }
    
    // Legacy method for backward compatibility
    @SuppressWarnings("unchecked")
    public <T> T getTypedResult() {
        return (T) result;
    }
    
    // Factory methods
    public static ToolExecutionResult success(String toolName, Object result) {
        return ToolExecutionResult.builder()
            .toolName(toolName)
            .result(result)
            .resultType(result != null ? result.getClass() : Void.class)
            .success(true)
            .build();
    }
    
    public static ToolExecutionResult failure(String toolName, String error) {
        return ToolExecutionResult.builder()
            .toolName(toolName)
            .success(false)
            .error(error)
            .build();
    }
}