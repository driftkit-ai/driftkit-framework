package ai.driftkit.workflow.engine.schema;

import ai.driftkit.workflow.engine.schema.AIFunctionSchema.AIFunctionProperty;
import ai.driftkit.workflow.engine.schema.annotations.SchemaClass;
import ai.driftkit.common.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Utility class for schema management and operations.
 * Ported from driftkit-chat-assistant-framework with adaptations for workflow engine.
 */
@Slf4j
public class SchemaUtils {
    private static final Map<Class<?>, AIFunctionSchema> schemaCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<AIFunctionSchema>> composableSchemaCache = new ConcurrentHashMap<>();
    private static final List<AIFunctionSchema> schemasList = new CopyOnWriteArrayList<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    // Schema registry for name->class mappings
    private static final SchemaRegistry SCHEMA_REGISTRY = new InMemorySchemaRegistry();

    /**
     * Gets all registered schemas.
     */
    public static List<AIFunctionSchema> getSchemas() {
        List<AIFunctionSchema> allSchemas = new ArrayList<>();
        allSchemas.addAll(schemasList);
        allSchemas.addAll(schemaCache.values());
        
        composableSchemaCache.values().forEach(allSchemas::addAll);
        
        return allSchemas;
    }

    /**
     * Adds a schema to the registry.
     */
    public static void addSchema(AIFunctionSchema schema) {
        schemasList.add(schema);
    }

    /**
     * Gets all schemas from a class, handling composable schemas.
     * Alias for generateComposableSchemas to match SchemaProvider interface.
     */
    public static List<AIFunctionSchema> generateComposableSchemas(Class<?> schemaClass) {
        return getAllSchemasFromClass(schemaClass);
    }
    
    /**
     * Gets all schemas from a class, handling composable schemas.
     */
    public static List<AIFunctionSchema> getAllSchemasFromClass(Class<?> schemaClass) {
        if (schemaClass == null || schemaClass == void.class) {
            return List.of();
        }
        
        SchemaClass schemaAnnotation = schemaClass.getAnnotation(SchemaClass.class);
        if (schemaAnnotation != null && schemaAnnotation.composable()) {
            if (composableSchemaCache.containsKey(schemaClass)) {
                return composableSchemaCache.get(schemaClass);
            }
            
            List<AIFunctionSchema> composableSchemas = createComposableSchemas(schemaClass, schemaAnnotation);
            composableSchemaCache.put(schemaClass, composableSchemas);
            return composableSchemas;
        } else {
            AIFunctionSchema schema = getSchemaFromClass(schemaClass);
            return schema != null ? List.of(schema) : List.of();
        }
    }

    /**
     * Creates composable schemas by splitting a class into individual field schemas.
     */
    private static List<AIFunctionSchema> createComposableSchemas(Class<?> schemaClass, SchemaClass annotation) {
        List<AIFunctionSchema> schemas = new ArrayList<>();
        
        AIFunctionSchema baseSchema = AIFunctionSchema.fromClass(schemaClass);
        
        String baseSchemaId = !annotation.id().isEmpty() ? annotation.id() : schemaClass.getSimpleName();
        
        for (AIFunctionProperty property : baseSchema.getProperties()) {
            AIFunctionSchema fieldSchema = new AIFunctionSchema(
                baseSchemaId + "_" + property.getName(),
                List.of(property)
            );
            
            fieldSchema.setDescription(property.getDescription());
            fieldSchema.setComposable(true);
            
            schemas.add(fieldSchema);
        }
        
        return schemas;
    }

    /**
     * Generates a schema from a class (alias for getSchemaFromClass).
     * Matches SchemaProvider interface.
     */
    public static AIFunctionSchema generateSchema(Class<?> schemaClass) {
        return getSchemaFromClass(schemaClass);
    }
    
    /**
     * Gets a schema from a class, using cache when possible.
     */
    public static AIFunctionSchema getSchemaFromClass(Class<?> schemaClass) {
        if (schemaClass == null || schemaClass == void.class) {
            return null;
        }
        
        if (schemaCache.containsKey(schemaClass)) {
            return schemaCache.get(schemaClass);
        }
        
        AIFunctionSchema schema = AIFunctionSchema.fromClass(schemaClass);
        
        SchemaClass annotation = schemaClass.getAnnotation(SchemaClass.class);
        if (annotation != null) {
            if (!annotation.id().isEmpty()) {
                schema.setSchemaName(annotation.id());
            }
            
            if (!annotation.description().isEmpty()) {
                schema.setDescription(annotation.description());
                log.debug("Schema description for {}: {}", schemaClass.getName(), annotation.description());
            }
            
            if (annotation.composable()) {
                schema.setComposable(true);
                log.debug("Schema {} is marked as composable", schemaClass.getName());
            }
        }
        
        // Check for @SchemaSystem annotation
        SchemaSystem schemaSystem = schemaClass.getAnnotation(SchemaSystem.class);
        if (schemaSystem != null) {
            schema.setSystem(schemaSystem.value());
            log.debug("Schema {} is marked as system: {}", schemaClass.getName(), schemaSystem.value());
        }
        
        schemaCache.put(schemaClass, schema);
        
        // Register in schema registry
        String schemaName = getSchemaId(schemaClass);
        if (schemaName != null) {
            SCHEMA_REGISTRY.registerSchema(schemaName, schemaClass);
            log.debug("Registered schema in registry: {} -> {}", schemaName, schemaClass.getName());
        }
        
        return schema;
    }
    
    /**
     * Gets the schema ID for a class.
     */
    public static String getSchemaId(Class<?> schemaClass) {
        if (schemaClass == null || schemaClass == void.class) {
            return null;
        }
        
        SchemaName schemaNameAnnotation = schemaClass.getAnnotation(SchemaName.class);
        if (schemaNameAnnotation != null) {
            return schemaNameAnnotation.value();
        }
        
        SchemaClass annotation = schemaClass.getAnnotation(SchemaClass.class);
        if (annotation != null && !annotation.id().isEmpty()) {
            return annotation.id();
        }
        
        return schemaClass.getSimpleName();
    }
    
    /**
     * Clears the schema cache.
     */
    public static void clearCache() {
        schemaCache.clear();
        composableSchemaCache.clear();
        AIFunctionSchema.clearCache();
        SCHEMA_REGISTRY.clear();
    }
    
    /**
     * Gets the Java class for a given schema name.
     * 
     * @param schemaName The name of the schema
     * @return The Java class if registered, null otherwise
     */
    public static Class<?> getSchemaClass(String schemaName) {
        if (schemaName == null) {
            return null;
        }
        return SCHEMA_REGISTRY.getSchemaClass(schemaName).orElse(null);
    }
    
    /**
     * Converts from map to object (alias for createInstance).
     * Matches SchemaProvider interface.
     */
    public static <T> T convertFromMap(Map<String, String> properties, Class<T> schemaClass) {
        return createInstance(schemaClass, properties);
    }
    
    /**
     * Creates an instance of a schema class from a map of properties.
     */
    public static <T> T createInstance(Class<T> schemaClass, Map<String, String> properties) {
        if (schemaClass == null || schemaClass == void.class) {
            return null;
        }
        
        try {
            T instance = schemaClass.getDeclaredConstructor().newInstance();
            
            if (properties != null && !properties.isEmpty()) {
                for (Field field : schemaClass.getDeclaredFields()) {
                    field.setAccessible(true);
                    String propertyName = field.getName();
                    
                    // Handle JsonAlias annotations
                    JsonAlias jsonAlias = field.getAnnotation(JsonAlias.class);
                    if (jsonAlias != null) {
                        String matchedProperty = null;
                        String matchedValue = null;
                        
                        if (properties.containsKey(propertyName)) {
                            matchedProperty = propertyName;
                            matchedValue = properties.get(propertyName);
                        } else {
                            for (String alias : jsonAlias.value()) {
                                if (properties.containsKey(alias)) {
                                    matchedProperty = alias;
                                    matchedValue = properties.get(alias);
                                    log.debug("Found property via JsonAlias: {} -> {} for field {}", 
                                            alias, matchedValue, propertyName);
                                    break;
                                }
                            }
                        }
                        
                        if (matchedProperty != null) {
                            setFieldValue(field, instance, matchedValue);
                        }
                    } else if (properties.containsKey(propertyName)) {
                        String propertyValue = properties.get(propertyName);
                        setFieldValue(field, instance, propertyValue);
                    }
                }
            }
            
            return instance;
        } catch (Exception e) {
            log.error("Error creating instance of {}: {}", schemaClass.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Converts object to map (alias for extractProperties).
     * Matches SchemaProvider interface.
     */
    public static Map<String, String> convertToMap(Object object) {
        return extractProperties(object);
    }
    
    /**
     * Extracts properties from an object into a map.
     */
    public static Map<String, String> extractProperties(Object object) {
        if (object == null) {
            return Map.of();
        }
        
        Map<String, String> properties = new HashMap<>();
        
        try {
            for (Field field : object.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                String propertyName = field.getName();
                Object value = field.get(object);
                
                if (value != null) {
                    if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum) {
                        properties.put(propertyName, value.toString());
                    } else if (value instanceof Collection || value instanceof Map || value.getClass().isArray()) {
                        try {
                            properties.put(propertyName, OBJECT_MAPPER.writeValueAsString(value));
                        } catch (Exception e) {
                            log.warn("Error serializing collection field {}: {}", propertyName, e.getMessage());
                            properties.put(propertyName, value.toString());
                        }
                    } else {
                        try {
                            properties.put(propertyName, OBJECT_MAPPER.writeValueAsString(value));
                        } catch (Exception e) {
                            log.warn("Error serializing complex field {}: {}", propertyName, e.getMessage());
                            properties.put(propertyName, value.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting properties from {}: {}", 
                    object.getClass().getName(), e.getMessage(), e);
        }
        
        return properties;
    }
    
    /**
     * Combines composable schema data, checking if all required fields are present.
     */
    public static Map<String, String> combineComposableSchemaData(
            Class<?> schemaClass, 
            Map<String, String> existingProperties, 
            Map<String, String> newProperties,
            String schemaId) {
        
        if (schemaClass == null) {
            return newProperties;
        }
        
        SchemaClass annotation = schemaClass.getAnnotation(SchemaClass.class);
        if (annotation == null || !annotation.composable()) {
            return newProperties;
        }
        
        Map<String, String> combinedProperties = existingProperties != null 
                ? new LinkedHashMap<>(existingProperties) 
                : new LinkedHashMap<>();
        
        if (newProperties != null) {
            combinedProperties.putAll(newProperties);
        }
        
        AIFunctionSchema schema = getSchemaFromClass(schemaClass);
        List<String> requiredFields = schema.getProperties().stream()
                .filter(AIFunctionProperty::isRequired)
                .map(AIFunctionProperty::getName)
                .collect(Collectors.toList());
        
        boolean isComplete = requiredFields.stream().allMatch(combinedProperties::containsKey);
        
        log.debug("Composable schema data for {}: combined={}, required={}, isComplete={}", 
                schemaId, combinedProperties.keySet(), requiredFields, isComplete);
        
        return isComplete ? combinedProperties : null;
    }
    
    /**
     * Sets a field value with type conversion.
     */
    private static void setFieldValue(Field field, Object instance, String value) {
        try {
            Class<?> fieldType = field.getType();
            
            if (fieldType == String.class) {
                field.set(instance, value);
            } else if (fieldType == int.class || fieldType == Integer.class) {
                field.set(instance, Integer.parseInt(value));
            } else if (fieldType == long.class || fieldType == Long.class) {
                field.set(instance, Long.parseLong(value));
            } else if (fieldType == double.class || fieldType == Double.class) {
                field.set(instance, Double.parseDouble(value));
            } else if (fieldType == float.class || fieldType == Float.class) {
                field.set(instance, Float.parseFloat(value));
            } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                field.set(instance, Boolean.parseBoolean(value));
            } else if (fieldType.isEnum()) {
                @SuppressWarnings("unchecked")
                Enum<?> enumValue = Enum.valueOf(fieldType.asSubclass(Enum.class), value);
                field.set(instance, enumValue);
            } else if (List.class.isAssignableFrom(fieldType)) {
                // Simple JSON array parsing
                if (value.startsWith("[") && value.endsWith("]")) {
                    try {
                        List<?> list = OBJECT_MAPPER.readValue(value, List.class);
                        field.set(instance, list);
                    } catch (Exception e) {
                        // Fallback to simple string split
                        String trimmedValue = value.substring(1, value.length() - 1);
                        String[] items = trimmedValue.split(",");
                        List<String> list = new ArrayList<>();
                        for (String item : items) {
                            list.add(item.trim().replaceAll("\"", ""));
                        }
                        field.set(instance, list);
                    }
                }
            } else if (Map.class.isAssignableFrom(fieldType)) {
                // Simple JSON object parsing
                if (value.startsWith("{") && value.endsWith("}")) {
                    try {
                        Map<?, ?> map = OBJECT_MAPPER.readValue(value, Map.class);
                        field.set(instance, map);
                    } catch (Exception e) {
                        log.warn("Failed to parse map value for field {}: {}", field.getName(), e.getMessage());
                    }
                }
            } else if (JsonUtils.isJSON(value)) {
                // If value is JSON and field type is an Object (custom class), try to parse it
                try {
                    Object parsedValue = JsonUtils.fromJson(value, fieldType);
                    field.set(instance, parsedValue);
                } catch (Exception e) {
                    log.warn("Failed to parse JSON value for field {} of type {}: {}", 
                            field.getName(), fieldType.getName(), e.getMessage());
                }
            } else {
                log.warn("Unsupported field type: {} for field: {}", fieldType.getName(), field.getName());
            }
        } catch (Exception e) {
            log.error("Error setting field {} with value '{}': {}", field.getName(), value, e.getMessage(), e);
        }
    }
}