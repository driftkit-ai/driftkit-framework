package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.schema.SchemaProvider;

/**
 * Holder class for workflow engine global components.
 * Provides static access to configured components like SchemaProvider.
 */
public final class WorkflowEngineHolder {
    
    private static SchemaProvider schemaProvider;
    
    private WorkflowEngineHolder() {
        // Utility class
    }
    
    /**
     * Sets the global schema provider.
     * 
     * @param provider The schema provider to use
     */
    public static void setSchemaProvider(SchemaProvider provider) {
        schemaProvider = provider;
    }
    
    /**
     * Gets the global schema provider.
     * 
     * @return The configured schema provider
     * @throws IllegalStateException if no schema provider is configured
     */
    public static SchemaProvider getSchemaProvider() {
        if (schemaProvider == null) {
            throw new IllegalStateException("SchemaProvider not configured. Initialize WorkflowEngine first.");
        }
        return schemaProvider;
    }
    
    /**
     * Clears the global schema provider (for testing).
     */
    public static void clear() {
        schemaProvider = null;
    }
}