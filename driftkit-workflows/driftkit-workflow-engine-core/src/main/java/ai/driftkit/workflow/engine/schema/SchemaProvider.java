package ai.driftkit.workflow.engine.schema;

import java.util.List;
import java.util.Map;

/**
 * Provider interface for schema generation and management in workflow steps.
 * Enables human-in-the-loop scenarios by generating schemas from Java classes.
 */
public interface SchemaProvider {
    
    /**
     * Generates an AIFunctionSchema from a Java class.
     * Direct port from SchemaUtils.getSchemaFromClass
     * 
     * @param inputType The class to generate schema from
     * @return The generated schema
     */
    AIFunctionSchema generateSchema(Class<?> inputType);
    
    /**
     * Generates composable schemas from a Java class.
     * Direct port from SchemaUtils.getAllSchemasFromClass
     * 
     * @param inputType The class to generate schemas from
     * @return List of composable schemas
     */
    List<AIFunctionSchema> generateComposableSchemas(Class<?> inputType);
    
    /**
     * Creates an instance of the target class from a map of properties.
     * Direct port from SchemaUtils.createInstance
     * 
     * @param data The property map
     * @param targetType The target class type
     * @return The created instance
     */
    <T> T convertFromMap(Map<String, String> data, Class<T> targetType);
    
    /**
     * Converts an object to a map of properties.
     * Direct port from SchemaUtils.extractProperties
     * 
     * @param data The object to convert
     * @return Map of property name to value
     */
    Map<String, String> convertToMap(Object data);
    
    /**
     * Combines composable schema data from multiple inputs.
     * Direct port from SchemaUtils.combineComposableSchemaData
     * 
     * @param schemaClass The schema class
     * @param existingProperties Existing properties
     * @param newProperties New properties to add
     * @param schemaId The schema identifier
     * @return Combined properties or null if incomplete
     */
    Map<String, String> combineComposableSchemaData(
        Class<?> schemaClass, 
        Map<String, String> existingProperties, 
        Map<String, String> newProperties,
        String schemaId);
    
    /**
     * Gets the schema ID for a class.
     * 
     * @param schemaClass The schema class
     * @return The schema ID
     */
    String getSchemaId(Class<?> schemaClass);
    
    /**
     * Gets all registered schemas.
     * 
     * @return List of all schemas
     */
    List<AIFunctionSchema> getAllSchemas();
    
    /**
     * Adds a schema to the registry.
     * 
     * @param schema The schema to add
     */
    void addSchema(AIFunctionSchema schema);
    
    /**
     * Clears the schema cache.
     */
    void clearCache();
    
    /**
     * Gets the Java class for a given schema name.
     * This allows looking up schema classes without querying suspension data.
     * 
     * @param schemaName The name of the schema
     * @return The Java class if registered, null otherwise
     */
    default Class<?> getSchemaClass(String schemaName) {
        return null;
    }
}