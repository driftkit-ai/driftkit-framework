package ai.driftkit.workflow.engine.domain;

import java.util.Map;

/**
 * Container for workflow suspension data that preserves type information
 * and original step input for proper resume handling.
 */
public record SuspensionData(
    Object promptToUser,
    Map<String, Object> metadata,
    Object originalStepInput,
    Class<?> originalStepInputType,
    String suspendedStepId,
    Class<?> nextInputClass
) {
    /**
     * Creates suspension data with type preservation.
     */
    public static SuspensionData create(
            Object promptToUser,
            Map<String, Object> metadata,
            Object originalStepInput,
            String suspendedStepId,
            Class<?> nextInputClass) {
        
        Class<?> inputType = originalStepInput != null ? 
            originalStepInput.getClass() : Object.class;
            
        return new SuspensionData(
            promptToUser,
            metadata,
            originalStepInput,
            inputType,
            suspendedStepId,
            nextInputClass
        );
    }

    /**
     * Checks if the original input matches the expected type.
     */
    public boolean hasOriginalInputOfType(Class<?> expectedType) {
        return originalStepInput != null && 
               expectedType.isAssignableFrom(originalStepInputType);
    }
    
    /**
     * Gets the original input cast to the specified type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOriginalInput(Class<T> type) {
        if (originalStepInput == null) {
            return null;
        }
        
        if (!type.isAssignableFrom(originalStepInputType)) {
            throw new ClassCastException(
                "Cannot cast original input from " + originalStepInputType.getName() + 
                " to " + type.getName()
            );
        }
        
        return type.cast(originalStepInput);
    }
}