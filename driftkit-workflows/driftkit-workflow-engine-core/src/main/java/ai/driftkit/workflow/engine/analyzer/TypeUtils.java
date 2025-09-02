package ai.driftkit.workflow.engine.analyzer;

import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for type analysis and conversion operations.
 * Contains static methods for type checking and ChatRequest conversions.
 */
@Slf4j
public final class TypeUtils {
    
    private TypeUtils() {
        // Utility class
    }
    
    /**
     * Extracts the result type from a StepResult generic type.
     * Delegates to MethodAnalyzer for the actual extraction.
     * 
     * @param type The generic type to extract from
     * @return The extracted type class
     */
    public static Class<?> extractStepResultType(Type type) {
        return MethodAnalyzer.extractStepResultType(type);
    }
    
    /**
     * Checks if a source type is compatible with a target type.
     * Delegates to TypeMatcher for the actual compatibility check.
     * 
     * @param sourceType The source type (output from previous step)
     * @param targetType The target type (input of next step)
     * @return true if types are compatible
     */
    public static boolean isTypeCompatible(Class<?> sourceType, Class<?> targetType) {
        return TypeMatcher.isTypeCompatible(sourceType, targetType);
    }
    
    /**
     * Checks if a type represents StepResult.Finish.
     * 
     * @param rawType The raw type to check
     * @return true if the type is StepResult.Finish
     */
    public static boolean isFinishType(Type rawType) {
        if (rawType instanceof Class<?> clazz) {
            return StepResult.Finish.class.isAssignableFrom(clazz);
        }
        return false;
    }
    
    /**
     * Converts input to the appropriate type for workflow execution.
     * Handles ChatRequest conversion based on schema name or workflow's expected input type.
     * 
     * @param input The input to convert
     * @param graph The workflow graph
     * @param workflowId The workflow ID for logging
     * @param <T> The expected type
     * @return The converted input or the original if no conversion needed
     */
    @SuppressWarnings("unchecked")
    public static <T> T convertInputForWorkflow(T input, WorkflowGraph<?, ?> graph, String workflowId) {
        if (!(input instanceof ChatRequest)) {
            return input;
        }
        
        ChatRequest chatRequest = (ChatRequest) input;
        
        // Try schema-based conversion first
        String schemaName = chatRequest.getRequestSchemaName();
        if (schemaName != null) {
            Class<?> schemaClass = SchemaUtils.getSchemaClass(schemaName);
            if (schemaClass != null) {
                Object converted = convertChatRequestToClass(chatRequest, schemaClass);
                if (converted != null) {
                    log.debug("Converted ChatRequest to {} using schema name: {}", 
                             schemaClass.getSimpleName(), schemaName);
                    return (T) converted;
                }
            }
            log.warn("Schema not found in registry: {}, using ChatRequest as-is", schemaName);
            return input;
        }
        
        // No schema name, try workflow's expected input type
        StepNode initialStep = graph.nodes().get(graph.initialStepId());
        if (initialStep == null || initialStep.executor() == null) {
            return input;
        }
        
        Class<?> expectedInputType = initialStep.executor().getInputType();
        if (expectedInputType == null || expectedInputType == void.class || 
            ChatRequest.class.isAssignableFrom(expectedInputType)) {
            return input;
        }
        
        Object converted = convertChatRequestToClass(chatRequest, expectedInputType);
        if (converted != null) {
            log.debug("Converted ChatRequest to {} for workflow {}", 
                    expectedInputType.getSimpleName(), workflowId);
            return (T) converted;
        }
        
        return input;
    }
    
    /**
     * Resolves the expected input type from a ChatRequest.
     * Used for resume operations where we need to determine the actual type.
     * 
     * @param chatRequest The chat request
     * @param defaultType The default type if no schema is found
     * @return The resolved input type class
     */
    public static Class<?> resolveInputType(ChatRequest chatRequest, Class<?> defaultType) {
        String schemaName = chatRequest.getRequestSchemaName();
        
        if (schemaName != null) {
            // Find the actual input class by schema name
            Class<?> actualInputClass = SchemaUtils.getSchemaClass(schemaName);
            if (actualInputClass != null) {
                log.debug("ChatRequest with schema {}, resolved to class {}",
                         schemaName, actualInputClass.getName());
                return actualInputClass;
            }
        }
        
        return defaultType;
    }
    
    /**
     * Converts ChatRequest to a specific class type.
     * 
     * @param chatRequest The chat request to convert
     * @param targetClass The target class to convert to
     * @return The converted object or null if conversion fails
     */
    public static Object convertChatRequestToClass(ChatRequest chatRequest, Class<?> targetClass) {
        try {
            return SchemaUtils.createInstance(targetClass, chatRequest.getPropertiesMap());
        } catch (Exception e) {
            log.warn("Failed to convert ChatRequest to {}: {}", 
                    targetClass.getSimpleName(), e.getMessage());
            return null;
        }
    }
}