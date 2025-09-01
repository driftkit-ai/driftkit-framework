package ai.driftkit.workflow.engine.utils;

import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import ai.driftkit.common.domain.chat.ChatRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for handling user input in workflow contexts.
 * Centralizes logic for type deserialization and compatibility checking.
 */
@Slf4j
public final class UserInputHandler {
    
    private UserInputHandler() {
        // Utility class
    }
    
    /**
     * Attempts to get user input from context with proper type deserialization.
     * Handles type compatibility checking and cleanup after retrieval.
     * 
     * @param instance The workflow instance
     * @param step The step expecting input
     * @return User input if available and compatible, null otherwise
     */
    public static Object getUserInputForStep(WorkflowInstance instance, StepNode step) {
        WorkflowContext ctx = instance.getContext();
        Class<?> expectedInputType = step.executor().getInputType();
        
        // Check if we have user input
        if (!ctx.hasStepResult(WorkflowContext.Keys.USER_INPUT)) {
            return null;
        }
        
        Object userInput = deserializeUserInput(ctx, expectedInputType);
        
        // Check if user input is compatible with step
        if (userInput != null && step.canAcceptInput(userInput.getClass())) {
            log.debug("Using user input of type {} for step {}",
                    userInput.getClass().getSimpleName(), step.id());
            // Remove userInput and its type from context after use
            clearUserInput(instance);
            return userInput;
        }
        
        return null;
    }
    
    /**
     * Deserializes user input with proper type handling.
     * Attempts to use saved type information for accurate deserialization.
     * 
     * @param ctx The workflow context
     * @param expectedInputType The expected input type for the step
     * @return Deserialized user input or null
     */
    private static Object deserializeUserInput(WorkflowContext ctx, Class<?> expectedInputType) {
        // Get the saved type information if available
        String userInputTypeName = ctx.getStepResultOrDefault(
                WorkflowContext.Keys.USER_INPUT_TYPE, String.class, null);

        // Try to deserialize with the correct type if we have type information
        if (userInputTypeName != null && expectedInputType != null) {
            try {
                Class<?> savedType = Class.forName(userInputTypeName);
                // Only use the saved type if it's compatible with the expected type
                if (expectedInputType.isAssignableFrom(savedType)) {
                    Object userInput = ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, savedType);
                    log.debug("Deserialized user input with saved type {}", savedType.getSimpleName());
                    return userInput;
                } else {
                    // Type mismatch - fall back to Object.class
                    log.warn("Saved type {} is not compatible with expected type {}",
                            savedType.getSimpleName(), expectedInputType.getSimpleName());
                    return ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, Object.class);
                }
            } catch (ClassNotFoundException e) {
                log.warn("Could not load saved type class: {}, falling back to Object.class",
                        userInputTypeName);
                return ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, Object.class);
            }
        } else {
            // No type information available - use Object.class as before
            return ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, Object.class);
        }
    }
    
    /**
     * Clears user input and type information from context.
     * Should be called after user input has been consumed.
     * 
     * @param instance The workflow instance
     */
    public static void clearUserInput(WorkflowInstance instance) {
        instance.updateContext(WorkflowContext.Keys.USER_INPUT, null);
        instance.updateContext(WorkflowContext.Keys.USER_INPUT_TYPE, null);
    }
    
    /**
     * Saves user input with type information to context.
     * Used when workflow is resumed with user-provided data.
     * Special handling for ChatRequest to convert to actual type.
     * 
     * @param instance The workflow instance
     * @param userInput The user input to save
     */
    public static void saveUserInput(WorkflowInstance instance, Object userInput) {
        if (userInput != null) {
            Object actualInput = userInput;
            
            // Special handling for ChatRequest - convert to actual type
            if (userInput instanceof ChatRequest) {
                ChatRequest chatRequest = (ChatRequest) userInput;
                String schemaName = chatRequest.getRequestSchemaName();
                
                if (schemaName != null) {
                    // Find the actual input class by schema name
                    Class<?> actualInputClass = SchemaUtils.getSchemaClass(schemaName);
                    if (actualInputClass != null) {
                        // Convert properties map to expected type
                        Object convertedInput = SchemaUtils.createInstance(
                            actualInputClass,
                            chatRequest.getPropertiesMap()
                        );
                        if (convertedInput != null) {
                            log.debug("Converted ChatRequest to {} using schema name: {}", 
                                     actualInputClass.getSimpleName(), schemaName);
                            actualInput = convertedInput;
                        } else {
                            log.warn("Failed to convert ChatRequest to {}, using ChatRequest as-is", 
                                    actualInputClass.getName());
                        }
                    } else {
                        log.warn("Schema not found in registry: {}, using ChatRequest as-is", schemaName);
                    }
                }
            }
            
            instance.updateContext(WorkflowContext.Keys.USER_INPUT, actualInput);
            instance.updateContext(WorkflowContext.Keys.USER_INPUT_TYPE, 
                                 actualInput.getClass().getName());
        }
    }
    
    /**
     * Checks if there is user input available in the context.
     * 
     * @param ctx The workflow context
     * @return true if user input exists, false otherwise
     */
    public static boolean hasUserInput(WorkflowContext ctx) {
        return ctx.hasStepResult(WorkflowContext.Keys.USER_INPUT);
    }
}