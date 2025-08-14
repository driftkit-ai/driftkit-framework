package ai.driftkit.workflow.engine.schema;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory implementation of SchemaRegistry using concurrent data structures.
 */
@Slf4j
public class InMemorySchemaRegistry implements SchemaRegistry {
    
    private final Map<String, Class<?>> nameToClass = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> classToName = new ConcurrentHashMap<>();
    
    @Override
    public void registerSchema(String schemaName, Class<?> schemaClass) {
        if (schemaName == null || schemaClass == null) {
            throw new IllegalArgumentException("Schema name and class cannot be null");
        }
        
        // Check for conflicts
        Class<?> existingClass = nameToClass.get(schemaName);
        if (existingClass != null && !existingClass.equals(schemaClass)) {
            log.warn("Overwriting schema registration for name '{}': {} -> {}", 
                schemaName, existingClass.getName(), schemaClass.getName());
        }
        
        String existingName = classToName.get(schemaClass);
        if (existingName != null && !existingName.equals(schemaName)) {
            log.warn("Overwriting schema registration for class {}: '{}' -> '{}'", 
                schemaClass.getName(), existingName, schemaName);
            // Remove old mapping
            nameToClass.remove(existingName);
        }
        
        nameToClass.put(schemaName, schemaClass);
        classToName.put(schemaClass, schemaName);
        
        log.debug("Registered schema '{}' -> {}", schemaName, schemaClass.getName());
    }
    
    @Override
    public Optional<Class<?>> getSchemaClass(String schemaName) {
        return Optional.ofNullable(nameToClass.get(schemaName));
    }
    
    @Override
    public Optional<String> getSchemaName(Class<?> schemaClass) {
        return Optional.ofNullable(classToName.get(schemaClass));
    }
    
    @Override
    public void clear() {
        nameToClass.clear();
        classToName.clear();
        log.debug("Cleared all schema registrations");
    }
    
    /**
     * Get the number of registered schemas.
     * 
     * @return The number of registered schemas
     */
    public int size() {
        return nameToClass.size();
    }
}