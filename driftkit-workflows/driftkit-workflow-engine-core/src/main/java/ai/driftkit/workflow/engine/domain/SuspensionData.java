package ai.driftkit.workflow.engine.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Container for workflow suspension data that preserves type information
 * and original step input for proper resume handling.
 */
public record SuspensionData(
    String messageId,
    Object promptToUser,
    Map<String, Object> metadata,
    Object originalStepInput,
    Class<?> originalStepInputType,
    String suspendedStepId,
    Class<?> nextInputClass
) {
    /**
     * Creates suspension data with type preservation and auto-generated message ID.
     */
    public static SuspensionData create(
            Object promptToUser,
            Map<String, Object> metadata,
            Object originalStepInput,
            String suspendedStepId,
            Class<?> nextInputClass) {
        
        String messageId = UUID.randomUUID().toString();
        Class<?> inputType = originalStepInput != null ? 
            originalStepInput.getClass() : Object.class;
            
        return new SuspensionData(
            messageId,
            promptToUser,
            metadata,
            originalStepInput,
            inputType,
            suspendedStepId,
            nextInputClass
        );
    }
    
    /**
     * Creates suspension data with explicit message ID.
     */
    public static SuspensionData createWithMessageId(
            String messageId,
            Object promptToUser,
            Map<String, Object> metadata,
            Object originalStepInput,
            String suspendedStepId,
            Class<?> nextInputClass) {
        
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Message ID cannot be null or blank");
        }
        
        Class<?> inputType = originalStepInput != null ? 
            originalStepInput.getClass() : Object.class;
            
        return new SuspensionData(
            messageId,
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