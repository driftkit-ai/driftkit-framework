package ai.driftkit.workflow.engine.schema;

import java.util.Optional;

/**
 * Registry for mapping schema names to their corresponding Java classes.
 * This eliminates the need to look up suspension data just to get schema information.
 */
public interface SchemaRegistry {
    
    /**
     * Register a schema class with a given name.
     * 
     * @param schemaName The name of the schema
     * @param schemaClass The Java class representing the schema
     */
    void registerSchema(String schemaName, Class<?> schemaClass);
    
    /**
     * Get the Java class for a given schema name.
     * 
     * @param schemaName The name of the schema
     * @return The Java class if registered, empty otherwise
     */
    Optional<Class<?>> getSchemaClass(String schemaName);
    
    /**
     * Get the schema name for a given Java class.
     * 
     * @param schemaClass The Java class
     * @return The schema name if registered, empty otherwise
     */
    Optional<String> getSchemaName(Class<?> schemaClass);
    
    /**
     * Check if a schema name is registered.
     * 
     * @param schemaName The name of the schema
     * @return true if registered, false otherwise
     */
    default boolean hasSchema(String schemaName) {
        return getSchemaClass(schemaName).isPresent();
    }
    
    /**
     * Clear all registered schemas.
     */
    void clear();
}