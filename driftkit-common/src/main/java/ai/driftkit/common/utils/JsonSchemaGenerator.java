package ai.driftkit.common.utils;

import ai.driftkit.common.annotation.JsonSchemaStrict;
import ai.driftkit.common.domain.client.ModelClient.ResponseFormatType;
import ai.driftkit.common.domain.client.ResponseFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Utility class for generating JSON schemas from Java classes.
 * Supports generating OpenAI-compatible structured output schemas.
 */
@Slf4j
public class JsonSchemaGenerator {

    private static final Set<Class<?>> PRIMITIVE_TYPES = Set.of(
            String.class, Integer.class, Long.class, Double.class, Float.class,
            Boolean.class, int.class, long.class, double.class, float.class, boolean.class
    );

    /**
     * Generates a JSON schema from a Java class.
     *
     * @param clazz The class to generate schema from
     * @return ResponseFormat.JsonSchema representing the class structure
     */
    public static ResponseFormat.JsonSchema generateSchema(Class<?> clazz) {
        return generateSchema(clazz, clazz.getSimpleName());
    }

    /**
     * Generates a JSON schema from a Java class with a custom title.
     *
     * @param clazz The class to generate schema from
     * @param title The title for the schema
     * @return ResponseFormat.JsonSchema representing the class structure
     */
    public static ResponseFormat.JsonSchema generateSchema(Class<?> clazz, String title) {
        ResponseFormat.JsonSchema schema = new ResponseFormat.JsonSchema();
        schema.setTitle(title);
        schema.setType(ResponseFormatType.Object.getType());
        schema.setAdditionalProperties(false);
        
        // Check if class is marked as strict
        boolean isStrict = clazz.isAnnotationPresent(JsonSchemaStrict.class);
        
        Map<String, ResponseFormat.SchemaProperty> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        
        // Process all fields including inherited ones
        List<Field> allFields = getAllFields(clazz);
        for (Field field : allFields) {
            // Skip static and transient fields
            if (Modifier.isStatic(field.getModifiers()) || 
                Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            
            String propertyName = getPropertyName(field);
            ResponseFormat.SchemaProperty property = generateProperty(field, isStrict);
            
            properties.put(propertyName, property);

            if (isStrict || isRequired(field)) {
                required.add(propertyName);
            }
        }
        
        schema.setProperties(properties);
        if (!required.isEmpty()) {
            schema.setRequired(required);
        }
        
        // Set strict flag if all properties are required
        if (!properties.isEmpty() && required.size() == properties.size()) {
            schema.setStrict(true);
        }
        
        return schema;
    }

    /**
     * Collects all fields from a class including inherited fields from parent classes.
     * 
     * @param clazz The class to get fields from
     * @return List of all fields including inherited ones
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        
        // Walk up the class hierarchy
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            // Add fields from current class
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            
            // Move to parent class
            currentClass = currentClass.getSuperclass();
        }
        
        return fields;
    }

    /**
     * Generates a property definition for a field.
     *
     * @param field The field to generate property for
     * @param isStrict Whether the parent class is in strict mode
     * @return ResponseFormat.SchemaProperty or null if field should be skipped
     */
    private static ResponseFormat.SchemaProperty generateProperty(Field field, boolean isStrict) {
        ResponseFormat.SchemaProperty property = new ResponseFormat.SchemaProperty();
        
        Class<?> fieldType = field.getType();
        Type genericType = field.getGenericType();
        
        // Set description from annotation if present
        JsonPropertyDescription descriptionAnnotation = field.getAnnotation(JsonPropertyDescription.class);
        if (descriptionAnnotation != null) {
            property.setDescription(descriptionAnnotation.value());
        }
        
        // Handle different types
        if (String.class.equals(fieldType)) {
            property.setType(ResponseFormatType.String.getType());
        } else if (Integer.class.equals(fieldType) || int.class.equals(fieldType) ||
                   Long.class.equals(fieldType) || long.class.equals(fieldType)) {
            property.setType(ResponseFormatType.Integer.getType());
        } else if (Double.class.equals(fieldType) || double.class.equals(fieldType) ||
                   Float.class.equals(fieldType) || float.class.equals(fieldType)) {
            property.setType(ResponseFormatType.Number.getType());
        } else if (Boolean.class.equals(fieldType) || boolean.class.equals(fieldType)) {
            property.setType(ResponseFormatType.Boolean.getType());
        } else if (fieldType.isEnum()) {
            property.setType(ResponseFormatType.String.getType());
            List<String> enumValues = new ArrayList<>();
            for (Object enumConstant : fieldType.getEnumConstants()) {
                enumValues.add(enumConstant.toString());
            }
            property.setEnumValues(enumValues);
        } else if (List.class.isAssignableFrom(fieldType) || Set.class.isAssignableFrom(fieldType)) {
            property.setType(ResponseFormatType.Array.getType());
            
            // Handle generic type for array items
            if (genericType instanceof ParameterizedType paramType) {
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> itemClass) {
                    ResponseFormat.SchemaProperty itemSchema = generateItemSchema(itemClass);
                    property.setItems(itemSchema);
                }
            }
        } else if (Map.class.isAssignableFrom(fieldType)) {
            property.setType(ResponseFormatType.Object.getType());
            
            // For maps, we can add additional properties schema if needed
            if (genericType instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) genericType;
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length == 2 && typeArgs[1] instanceof Class) {
                    Class<?> valueClass = (Class<?>) typeArgs[1];
                    if (PRIMITIVE_TYPES.contains(valueClass)) {
                        property.setAdditionalProperties(Map.of("type", getTypeString(valueClass)));
                    }
                }
            }
        } else if (!PRIMITIVE_TYPES.contains(fieldType)) {
            // Nested object
            property.setType(ResponseFormatType.Object.getType());
            ResponseFormat.JsonSchema nestedSchema = generateSchema(fieldType);
            property.setProperties(nestedSchema.getProperties());
            property.setRequired(nestedSchema.getRequired());
            property.setAdditionalProperties(false);
        }
        
        return property;
    }

    /**
     * Generates an item schema for array elements.
     *
     * @param itemClass The class of array items
     * @return ResponseFormat.SchemaProperty for the item
     */
    private static ResponseFormat.SchemaProperty generateItemSchema(Class<?> itemClass) {
        ResponseFormat.SchemaProperty itemSchema = new ResponseFormat.SchemaProperty();
        
        if (String.class.equals(itemClass)) {
            itemSchema.setType(ResponseFormatType.String.getType());
        } else if (Integer.class.equals(itemClass) || Long.class.equals(itemClass)) {
            itemSchema.setType(ResponseFormatType.Integer.getType());
        } else if (Double.class.equals(itemClass) || Float.class.equals(itemClass)) {
            itemSchema.setType(ResponseFormatType.Number.getType());
        } else if (Boolean.class.equals(itemClass)) {
            itemSchema.setType(ResponseFormatType.Boolean.getType());
        } else if (itemClass.isEnum()) {
            itemSchema.setType(ResponseFormatType.String.getType());
            List<String> enumValues = new ArrayList<>();
            for (Object enumConstant : itemClass.getEnumConstants()) {
                enumValues.add(enumConstant.toString());
            }
            itemSchema.setEnumValues(enumValues);
        } else if (!PRIMITIVE_TYPES.contains(itemClass)) {
            // Nested object in array
            ResponseFormat.JsonSchema nestedSchema = generateSchema(itemClass);
            itemSchema.setType(ResponseFormatType.Object.getType());
            itemSchema.setProperties(nestedSchema.getProperties());
            itemSchema.setRequired(nestedSchema.getRequired());
            itemSchema.setAdditionalProperties(false);
        }
        
        return itemSchema;
    }

    /**
     * Gets the property name for a field, considering JsonProperty annotation.
     *
     * @param field The field to get property name for
     * @return The property name
     */
    private static String getPropertyName(Field field) {
        JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        if (jsonProperty != null && !jsonProperty.value().isEmpty()) {
            return jsonProperty.value();
        }
        return field.getName();
    }

    /**
     * Checks if a field is required (not nullable).
     *
     * @param field The field to check
     * @return true if field is required
     */
    private static boolean isRequired(Field field) {
        // Check for @NotNull annotation
        if (field.isAnnotationPresent(NotNull.class)) {
            return true;
        }
        
        // Check for @JsonProperty(required = true)
        JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        if (jsonProperty != null && jsonProperty.required()) {
            return true;
        }
        
        // Check for lombok @NonNull
        if (field.isAnnotationPresent(lombok.NonNull.class)) {
            return true;
        }
        
        // Primitive types are always required
        return field.getType().isPrimitive();
    }

    /**
     * Gets the JSON type string for a Java class.
     *
     * @param clazz The class to get type for
     * @return The JSON type string
     */
    private static String getTypeString(Class<?> clazz) {
        if (String.class.equals(clazz)) {
            return ResponseFormatType.String.getType();
        } else if (Integer.class.equals(clazz) || Long.class.equals(clazz) ||
                   int.class.equals(clazz) || long.class.equals(clazz)) {
            return ResponseFormatType.Integer.getType();
        } else if (Double.class.equals(clazz) || Float.class.equals(clazz) ||
                   double.class.equals(clazz) || float.class.equals(clazz)) {
            return ResponseFormatType.Number.getType();
        } else if (Boolean.class.equals(clazz) || boolean.class.equals(clazz)) {
            return ResponseFormatType.Boolean.getType();
        } else if (clazz.isEnum()) {
            return ResponseFormatType.String.getType();
        } else {
            return ResponseFormatType.Object.getType();
        }
    }

}