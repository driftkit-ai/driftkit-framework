package ai.driftkit.workflow.engine.schema;

import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema.AIFunctionProperty;
import ai.driftkit.workflow.engine.schema.annotations.SchemaClass;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Default implementation of SchemaProvider, ported from SchemaUtils.
 */
@Slf4j
public class DefaultSchemaProvider implements SchemaProvider {
    private static final Map<Class<?>, AIFunctionSchema> schemaCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<AIFunctionSchema>> composableSchemaCache = new ConcurrentHashMap<>();
    private static final List<AIFunctionSchema> schemasList = new CopyOnWriteArrayList<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Use static registry to share schemas across instances
    private static final SchemaRegistry GLOBAL_SCHEMA_REGISTRY = new InMemorySchemaRegistry();
    private final SchemaRegistry schemaRegistry;
    
    /**
     * Default constructor with in-memory schema registry.
     */
    public DefaultSchemaProvider() {
        this(GLOBAL_SCHEMA_REGISTRY);
    }
    
    /**
     * Constructor with custom schema registry.
     * 
     * @param schemaRegistry The schema registry to use
     */
    public DefaultSchemaProvider(SchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public AIFunctionSchema generateSchema(Class<?> inputType) {
        if (inputType == null || inputType == void.class) {
            return null;
        }
        
        if (schemaCache.containsKey(inputType)) {
            return schemaCache.get(inputType);
        }
        
        AIFunctionSchema schema = AIFunctionSchema.fromClass(inputType);
        
        SchemaClass annotation = inputType.getAnnotation(SchemaClass.class);
        if (annotation != null) {
            if (!annotation.id().isEmpty()) {
                schema.setSchemaName(annotation.id());
            }
            
            if (!annotation.description().isEmpty()) {
                schema.setDescription(annotation.description());
                log.debug("Schema description for {}: {}", inputType.getName(), annotation.description());
            }
            
            if (annotation.composable()) {
                schema.setComposable(true);
                log.debug("Schema {} is marked as composable", inputType.getName());
            }
        }
        
        schemaCache.put(inputType, schema);
        
        // Register in schema registry
        String schemaName = getSchemaId(inputType);
        if (schemaName != null) {
            schemaRegistry.registerSchema(schemaName, inputType);
            log.debug("Registered schema in registry: {} -> {}", schemaName, inputType.getName());
        }
        
        return schema;
    }
    
    @Override
    public List<AIFunctionSchema> generateComposableSchemas(Class<?> inputType) {
        if (inputType == null || inputType == void.class) {
            return List.of();
        }
        
        SchemaClass schemaAnnotation = inputType.getAnnotation(SchemaClass.class);
        if (schemaAnnotation != null && schemaAnnotation.composable()) {
            if (composableSchemaCache.containsKey(inputType)) {
                return composableSchemaCache.get(inputType);
            }
            
            List<AIFunctionSchema> composableSchemas = createComposableSchemas(inputType, schemaAnnotation);
            composableSchemaCache.put(inputType, composableSchemas);
            return composableSchemas;
        } else {
            return List.of(generateSchema(inputType));
        }
    }
    
    private List<AIFunctionSchema> createComposableSchemas(Class<?> schemaClass, SchemaClass annotation) {
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
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFromMap(Map<String, String> properties, Class<T> targetType) {
        if (targetType == null || targetType == void.class) {
            return null;
        }
        
        try {
            T instance = targetType.getDeclaredConstructor().newInstance();
            
            if (properties != null && !properties.isEmpty()) {
                for (Field field : targetType.getDeclaredFields()) {
                    field.setAccessible(true);
                    String propertyName = field.getName();
                    
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
            log.error("Error creating instance of {}: {}", targetType.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public Map<String, String> convertToMap(Object object) {
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
                            properties.put(propertyName, objectMapper.writeValueAsString(value));
                        } catch (Exception e) {
                            log.warn("Error serializing collection field {}: {}", propertyName, e.getMessage());
                            properties.put(propertyName, value.toString());
                        }
                    } else {
                        try {
                            properties.put(propertyName, objectMapper.writeValueAsString(value));
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
    
    @Override
    public Map<String, String> combineComposableSchemaData(
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
        
        AIFunctionSchema schema = generateSchema(schemaClass);
        List<String> requiredFields = schema.getProperties().stream()
                .filter(AIFunctionProperty::isRequired)
                .map(AIFunctionProperty::getName)
                .collect(Collectors.toList());
        
        boolean isComplete = requiredFields.stream().allMatch(combinedProperties::containsKey);
        
        log.debug("Composable schema data for {}: combined={}, required={}, isComplete={}", 
                schemaId, combinedProperties.keySet(), requiredFields, isComplete);
        
        return isComplete ? combinedProperties : null;
    }
    
    @Override
    public String getSchemaId(Class<?> schemaClass) {
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
    
    @Override
    public List<AIFunctionSchema> getAllSchemas() {
        List<AIFunctionSchema> allSchemas = new ArrayList<>();
        allSchemas.addAll(schemasList);
        allSchemas.addAll(schemaCache.values());
        
        composableSchemaCache.values().forEach(allSchemas::addAll);
        
        return allSchemas;
    }
    
    @Override
    public void addSchema(AIFunctionSchema schema) {
        schemasList.add(schema);
    }
    
    @Override
    public void clearCache() {
        schemaCache.clear();
        composableSchemaCache.clear();
        AIFunctionSchema.clearCache();
        schemaRegistry.clear();
    }
    
    @Override
    public Class<?> getSchemaClass(String schemaName) {
        if (schemaName == null) {
            return null;
        }
        return schemaRegistry.getSchemaClass(schemaName).orElse(null);
    }
    
    private void setFieldValue(Field field, Object instance, String value) {
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
                if (value.startsWith("[") && value.endsWith("]")) {
                    String trimmedValue = value.substring(1, value.length() - 1);
                    String[] items = trimmedValue.split(",");
                    List<String> list = new ArrayList<>();
                    for (String item : items) {
                        list.add(item.trim());
                    }
                    field.set(instance, list);
                }
            } else if (Map.class.isAssignableFrom(fieldType)) {
                if (value.startsWith("{") && value.endsWith("}")) {
                    Map<String, String> map = new HashMap<>();
                    String trimmedValue = value.substring(1, value.length() - 1);
                    String[] pairs = trimmedValue.split(",");
                    for (String pair : pairs) {
                        String[] keyValue = pair.split(":");
                        if (keyValue.length == 2) {
                            map.put(keyValue[0].trim(), keyValue[1].trim());
                        }
                    }
                    field.set(instance, map);
                }
            } else if (JsonUtils.isJSON(value)) {
                // If value is JSON and field type is an Object (custom class), try to parse it
                try {
                    Object parsedValue = JsonUtils.fromJson(value, fieldType);
                    field.set(instance, parsedValue);
                } catch (Exception e) {
                    log.warn("Failed to parse JSON value for field {} of type {}: {}", field.getName(), fieldType.getName(), e.getMessage());
                }
            } else {
                log.warn("Unsupported field type: {}", fieldType.getName());
            }
        } catch (Exception e) {
            log.error("Error setting field {}: {}", field.getName(), e.getMessage(), e);
        }
    }
}