package ai.driftkit.workflow.engine.utils;

import ai.driftkit.common.domain.chat.ChatMessage.DataProperty;
import ai.driftkit.workflow.engine.builder.InternalRoutingMarker;
import ai.driftkit.workflow.engine.core.StepOutput;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import ai.driftkit.workflow.engine.analyzer.TypeUtils;
import ai.driftkit.common.domain.chat.ChatRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for handling user input and input resolution in workflow contexts.
 * Centralizes logic for type deserialization, compatibility checking, and finding suitable inputs from history.
 */
@Slf4j
public final class WorkflowInputOutputHandler {
    
    private WorkflowInputOutputHandler() {
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
                    // Use TypeUtils to resolve the actual input type
                    Class<?> actualInputClass = TypeUtils.resolveInputType(chatRequest, null);
                    if (actualInputClass != null) {
                        // Convert using TypeUtils
                        Object convertedInput = TypeUtils.convertChatRequestToClass(chatRequest, actualInputClass);
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
    
    /**
     * Finds the most recent compatible output from execution history.
     * Prioritizes exact type matches over compatible types.
     * 
     * @param instance The workflow instance
     * @param targetStep The step expecting input
     * @return Compatible output if found, null otherwise
     */
    public static Object findCompatibleOutputFromHistory(WorkflowInstance instance, StepNode targetStep) {
        WorkflowContext ctx = instance.getContext();
        Class<?> expectedInputType = targetStep.executor().getInputType();
        List<WorkflowInstance.StepExecutionRecord> history = instance.getExecutionHistory();
        
        log.debug("Finding compatible output for step {} expecting type: {}", 
            targetStep.id(), expectedInputType != null ? expectedInputType.getSimpleName() : "any");
        
        if (history.isEmpty()) {
            return null;
        }
        
        // First pass: Look for exact type match
        Object exactMatch = findExactTypeMatch(history, ctx, targetStep, expectedInputType);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // Second pass: Look for compatible type
        return findCompatibleTypeMatch(history, ctx, targetStep);
    }
    
    /**
     * Finds an exact type match from execution history.
     */
    private static Object findExactTypeMatch(List<WorkflowInstance.StepExecutionRecord> history,
                                           WorkflowContext ctx,
                                           StepNode targetStep,
                                           Class<?> expectedInputType) {
        if (expectedInputType == null || expectedInputType == Object.class) {
            return null;
        }
        
        // Traverse history from most recent to oldest
        for (int i = history.size() - 1; i >= 0; i--) {
            WorkflowInstance.StepExecutionRecord exec = history.get(i);
            
            // Skip if it's the target step itself
            if (exec.getStepId().equals(targetStep.id())) {
                continue;
            }
            
            StepOutput output = getStepOutput(ctx, exec.getStepId());
            if (output != null && !isRoutingMarker(output) && output.getActualClass().equals(expectedInputType)) {
                Object result = output.getValue();
                log.debug("Found exact type match from step {} (type: {}) for step {}",
                        exec.getStepId(), output.getActualClass().getSimpleName(), targetStep.id());
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Finds a compatible type match from execution history.
     */
    private static Object findCompatibleTypeMatch(List<WorkflowInstance.StepExecutionRecord> history,
                                                WorkflowContext ctx,
                                                StepNode targetStep) {
        // Traverse history from most recent to oldest
        for (int i = history.size() - 1; i >= 0; i--) {
            WorkflowInstance.StepExecutionRecord exec = history.get(i);
            
            // Skip if it's the target step itself
            if (exec.getStepId().equals(targetStep.id())) {
                continue;
            }
            
            StepOutput output = getStepOutput(ctx, exec.getStepId());
            if (output != null && !isRoutingMarker(output) && targetStep.canAcceptInput(output.getActualClass())) {
                Object result = output.getValue();
                log.debug("Found compatible output from step {} (type: {}) for step {}",
                        exec.getStepId(), output.getActualClass().getSimpleName(), targetStep.id());
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Safely retrieves step output from context.
     */
    private static StepOutput getStepOutput(WorkflowContext ctx, String stepId) {
        if (!ctx.hasStepResult(stepId)) {
            return null;
        }
        
        return ctx.getStepOutputs().get(stepId);
    }
    
    /**
     * Checks if the output is a routing marker (used for branch decisions).
     * These should not be used as input for subsequent steps.
     * 
     * @param output The step output to check
     * @return true if this is a routing marker
     */
    private static boolean isRoutingMarker(StepOutput output) {
        if (output == null || !output.hasValue()) {
            return false;
        }
        
        Object value = output.getValue();
        if (value == null) {
            return false;
        }
        
        // Check if it implements InternalRoutingMarker interface
        return value instanceof InternalRoutingMarker;
    }
    
    /**
     * Gets a human-readable type name for logging.
     * 
     * @param type The class type
     * @return Simple name or "any" for null/Object types
     */
    public static String getTypeDisplayName(Class<?> type) {
        if (type == null || type == Object.class) {
            return "any";
        }
        return type.getSimpleName();
    }
    
    /**
     * Extracts properties from workflow data objects.
     * Handles various data formats including Maps with DataProperty lists,
     * direct property maps, and domain objects.
     * 
     * @param data The data to extract properties from
     * @return Map of string key-value pairs
     */
    public static Map<String, String> extractPropertiesFromData(Object data) {
        if (data == null) {
            return new HashMap<>();
        }

        // If data is already a properties map from a workflow response object
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            // Check if this is a response object with properties
            Object propsObj = map.get("properties");
            if (propsObj instanceof List) {
                // Handle DataProperty list
                Map<String, String> properties = new HashMap<>();
                List<?> propsList = (List<?>) propsObj;
                for (Object prop : propsList) {
                    if (prop instanceof DataProperty dp) {
                        properties.put(dp.getName(), dp.getValue());
                    }
                }
                return properties;
            } else if (propsObj instanceof Map) {
                // Direct property map in "properties" field
                Map<String, String> properties = new HashMap<>();
                Map<?, ?> propsMap = (Map<?, ?>) propsObj;
                propsMap.forEach((k, v) -> {
                    if (k != null && v != null) {
                        properties.put(k.toString(), v.toString());
                    }
                });
                return properties;
            } else {
                // No "properties" field, treat as plain map
                Map<String, String> properties = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        properties.put(k.toString(), v.toString());
                    }
                });
                return properties;
            }
        } else {
            // Not a map, use schema utils to extract properties from domain object
            return SchemaUtils.extractProperties(data);
        }
    }
}