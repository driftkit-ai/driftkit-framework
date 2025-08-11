package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.DefaultSchemaProvider;
import ai.driftkit.workflow.engine.schema.SchemaProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for creating Suspend results with proper schema information.
 * This bridges the gap between the old StepEvent pattern and new StepResult pattern.
 */
public class SuspendHelper {
    
    // Default schema provider used in 99% of cases
    private static final SchemaProvider DEFAULT_SCHEMA_PROVIDER = new DefaultSchemaProvider();
    
    // Custom schema provider that can be set for special cases
    private static SchemaProvider customSchemaProvider = null;
    
    /**
     * Sets a custom SchemaProvider for all subsequent calls.
     * Pass null to revert to the DefaultSchemaProvider.
     * 
     * @param provider The custom SchemaProvider to use, or null for default
     */
    public static void setSchemaProvider(SchemaProvider provider) {
        customSchemaProvider = provider;
    }
    
    /**
     * Gets the current SchemaProvider (custom if set, otherwise default).
     * 
     * @return The current SchemaProvider
     */
    private static SchemaProvider getSchemaProvider() {
        return customSchemaProvider != null ? customSchemaProvider : DEFAULT_SCHEMA_PROVIDER;
    }
    
    /**
     * Creates a Suspend result that includes schema information for the next expected input.
     * This mimics the old framework's StepEvent.of(data, nextInputClass) pattern.
     * 
     * @param promptData The data to send to the user (will be converted to properties)
     * @param nextInputClass The class type expected as input when resumed
     * @param schemaProvider The schema provider to generate schema
     * @return A Suspend result with proper metadata
     */
    public static <T> StepResult.Suspend<T> suspendForInput(
            Object promptData, 
            Class<?> nextInputClass,
            SchemaProvider schemaProvider) {
        
        Map<String, Object> metadata = new HashMap<>();
        
        // Store the expected input class
        metadata.put("nextInputClass", nextInputClass.getName());
        
        // Generate the schema (required)
        if (schemaProvider == null) {
            throw new IllegalStateException("SchemaProvider is required for creating Suspend results");
        }
        
        AIFunctionSchema schema = schemaProvider.generateSchema(nextInputClass);
        if (schema == null) {
            throw new IllegalStateException("Failed to generate schema for class: " + nextInputClass.getName());
        }
        
        return new StepResult.Suspend<T>((T) promptData, nextInputClass, schema, metadata);
    }
    
    /**
     * Creates a Suspend result for simple prompts without complex data.
     * 
     * @param message The message to display to the user
     * @param nextInputClass The class type expected as input when resumed
     * @param schemaProvider The schema provider to generate schema
     * @return A Suspend result with proper metadata
     */
    public static <T> StepResult.Suspend<T> suspendWithMessage(
            String message,
            Class<?> nextInputClass,
            SchemaProvider schemaProvider) {
        
        Map<String, String> promptData = new HashMap<>();
        promptData.put("message", message);
        
        return suspendForInput(promptData, nextInputClass, schemaProvider);
    }
    
    /**
     * Creates a Suspend result that indicates waiting for user input of a specific type.
     * This is the most common pattern from the old framework.
     * 
     * @param responseData The response data to send (will extract properties)
     * @param nextInputClass The class type expected as input when resumed
     * @return A Suspend result with proper metadata
     */
    public static <T> StepResult.Suspend<T> waitForUserInput(
            Object responseData,
            Class<?> nextInputClass) {
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("nextInputClass", nextInputClass.getName());
        metadata.put("waitingForUserInput", true);
        
        SchemaProvider provider = getSchemaProvider();
        AIFunctionSchema schema = provider.generateSchema(nextInputClass);
        if (schema == null) {
            throw new IllegalStateException("Failed to generate schema for class: " + nextInputClass.getName());
        }
        
        return new StepResult.Suspend<T>((T) responseData, nextInputClass, schema, metadata);
    }
    
    /**
     * Creates a Suspend result that includes schema information for the next expected input.
     * Uses the default SchemaProvider.
     * 
     * @param promptData The data to send to the user (will be converted to properties)
     * @param nextInputClass The class type expected as input when resumed
     * @return A Suspend result with proper metadata
     */
    public static <T> StepResult.Suspend<T> suspendForInput(
            Object promptData, 
            Class<?> nextInputClass) {
        return suspendForInput(promptData, nextInputClass, getSchemaProvider());
    }
    
    /**
     * Creates a Suspend result for simple prompts without complex data.
     * Uses the default SchemaProvider.
     * 
     * @param message The message to display to the user
     * @param nextInputClass The class type expected as input when resumed
     * @return A Suspend result with proper metadata
     */
    public static <T> StepResult.Suspend<T> suspendWithMessage(
            String message,
            Class<?> nextInputClass) {
        return suspendWithMessage(message, nextInputClass, getSchemaProvider());
    }
}