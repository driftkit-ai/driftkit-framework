package ai.driftkit.workflow.engine.schema;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIFunctionSchema implements Serializable {
    private static final Map<Class<?>, AIFunctionSchema> schemaCache = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<Class<?>>> processingClasses = ThreadLocal.withInitial(HashSet::new);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    String schemaName;
    String description;
    List<AIFunctionProperty> properties;
    boolean isArray;
    boolean composable;
    
    @JsonIgnore
    Class<?> targetClass;

    public AIFunctionSchema(List<AIFunctionProperty> properties) {
        this.properties = properties;
    }

    public AIFunctionSchema(String schemaName, List<AIFunctionProperty> properties) {
        this(properties);
        this.schemaName = schemaName;
    }

    public static AIFunctionSchema fromClass(Class<?> clazz) {
        return fromClass(clazz, SchemaGenerationStrategy.RECURSIVE);
    }
    
    public static AIFunctionSchema fromClass(Class<?> clazz, SchemaGenerationStrategy strategy) {
        if (strategy == SchemaGenerationStrategy.JACKSON) {
            return fromClassUsingJackson(clazz);
        } else {
            return fromClassRecursive(clazz, null);
        }
    }
    
    private static AIFunctionSchema fromClassUsingJackson(Class<?> clazz) {
        if (schemaCache.containsKey(clazz)) {
            return schemaCache.get(clazz);
        }
        
        try {
            AIFunctionSchema schema = fromClassRecursive(clazz, null);
            schemaCache.put(clazz, schema);
            return schema;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate schema for class: " + clazz.getName(), e);
        }
    }
    
    private static AIFunctionSchema fromClassRecursive(Class<?> clazz, Type genericType) {
        if (schemaCache.containsKey(clazz)) {
            return schemaCache.get(clazz);
        }
        
        Set<Class<?>> currentlyProcessing = processingClasses.get();
        if (currentlyProcessing.contains(clazz)) {
            return new AIFunctionSchema(clazz.getSimpleName(), new ArrayList<>());
        }
        
        currentlyProcessing.add(clazz);
        
        try {
            List<AIFunctionProperty> properties = new ArrayList<>();
            String schemaName = clazz.getSimpleName();
            
            SchemaName schemaNameAnnotation = clazz.getAnnotation(SchemaName.class);
            if (schemaNameAnnotation != null && StringUtils.isNotBlank(schemaNameAnnotation.value())) {
                schemaName = schemaNameAnnotation.value();
            }
            
            String description = null;
            SchemaDescription descriptionAnnotation = clazz.getAnnotation(SchemaDescription.class);
            if (descriptionAnnotation != null && StringUtils.isNotBlank(descriptionAnnotation.value())) {
                description = descriptionAnnotation.value();
            }
            
            Class<?> currentClass = clazz;
            while (currentClass != null && currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || 
                        Modifier.isTransient(field.getModifiers()) || 
                        field.isSynthetic()) {
                        continue;
                    }
                    
                    if (field.isAnnotationPresent(JsonIgnore.class) || 
                        field.isAnnotationPresent(SchemaIgnore.class)) {
                        continue;
                    }
                    
                    field.setAccessible(true);
                    AIFunctionProperty property = createPropertyFromField(field);
                    if (property != null) {
                        properties.add(property);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            
            AIFunctionSchema schema = new AIFunctionSchema(schemaName, properties);
            schema.setTargetClass(clazz);
            
            if (description != null) {
                schema.setDescription(description);
            }
            
            SchemaArray schemaArrayAnnotation = clazz.getAnnotation(SchemaArray.class);
            if (schemaArrayAnnotation != null) {
                schema.setArray(true);
            }
            
            schemaCache.put(clazz, schema);
            return schema;
        } finally {
            currentlyProcessing.remove(clazz);
        }
    }
    
    private static AIFunctionProperty createPropertyFromField(Field field) {
        String name = field.getName();
        Class<?> type = field.getType();
        Type genericType = field.getGenericType();
        
        SchemaProperty propertyAnnotation = field.getAnnotation(SchemaProperty.class);
        
        AIFunctionProperty.AIFunctionPropertyBuilder builder = AIFunctionProperty.builder()
                .name(name)
                .nameId(name);
        
        if (propertyAnnotation != null) {
            if (StringUtils.isNotBlank(propertyAnnotation.nameId())) {
                builder.nameId(propertyAnnotation.nameId());
            }

            if (StringUtils.isNotBlank(propertyAnnotation.dataNameId())) {
                builder.dataNameId(propertyAnnotation.dataNameId());
            }

            if (StringUtils.isNotBlank(propertyAnnotation.description())) {
                builder.description(propertyAnnotation.description());
            } else {
                builder.description("Property " + name);
            }
            
            if (StringUtils.isNotBlank(propertyAnnotation.defaultValue())) {
                builder.defaultValue(propertyAnnotation.defaultValue());
            }
            
            if (propertyAnnotation.minValue() != Integer.MIN_VALUE) {
                builder.minValue(propertyAnnotation.minValue());
            }
            
            if (propertyAnnotation.maxValue() != Integer.MAX_VALUE) {
                builder.maxValue(propertyAnnotation.maxValue());
            }
            
            if (propertyAnnotation.minLength() > 0) {
                builder.minLength(propertyAnnotation.minLength());
            }
            
            if (propertyAnnotation.maxLength() > 0) {
                builder.maxLength(propertyAnnotation.maxLength());
            }
            
            builder.isRequired(propertyAnnotation.required());
            builder.isMultiSelect(propertyAnnotation.multiSelect());
            builder.isArray(propertyAnnotation.array());
            
            builder.valueAsNameId(propertyAnnotation.valueAsNameId());

            if (propertyAnnotation.values() != null && propertyAnnotation.values().length > 0) {
                builder.type(PropertyType.ENUM);
                builder.values(List.of(propertyAnnotation.values()));
            } else if (propertyAnnotation.type() != Void.class) {
                if (propertyAnnotation.type().isEnum()) {
                    setEnumValues(propertyAnnotation.type(), builder);
                }
            }
        } else {
            builder.description("Property " + name);
        }
        
        if (type.isArray()) {
            builder.isArray(true);
            Class<?> componentType = type.getComponentType();
            if (isSimpleType(componentType)) {
                builder.type(mapClassToPropertyType(componentType));
            } else {
                builder.type(PropertyType.OBJECT);
                builder.nestedSchema(fromClassRecursive(componentType, null));
            }
        }

        if (type.isEnum() && CollectionUtils.isEmpty(builder.values)) {
            setEnumValues(type, builder);
        }

        if (genericType.getClass().isEnum()) {
            setEnumValues(genericType.getClass(), builder);
        }

        else if (Collection.class.isAssignableFrom(type)) {
            builder.isArray(true);
            if (genericType instanceof ParameterizedType paramType) {
                Type actualTypeArg = paramType.getActualTypeArguments()[0];
                if (actualTypeArg instanceof Class<?> genericClass) {
                    if (isSimpleType(genericClass)) {
                        builder.type(mapClassToPropertyType(genericClass));
                    } else {
                        builder.type(PropertyType.OBJECT);
                        builder.nestedSchema(fromClassRecursive(genericClass, null));
                    }
                } else if (actualTypeArg instanceof ParameterizedType nestedParamType) {
                    Class<?> rawType = (Class<?>) nestedParamType.getRawType();
                    if (Collection.class.isAssignableFrom(rawType)) {
                        builder.type(PropertyType.ARRAY);
                        Type nestedTypeArg = nestedParamType.getActualTypeArguments()[0];
                        if (nestedTypeArg instanceof Class<?> nestedClass) {
                            AIFunctionSchema nestedSchema = new AIFunctionSchema();
                            nestedSchema.setArray(true);
                            if (isSimpleType(nestedClass)) {
                                nestedSchema.setSchemaName(nestedClass.getSimpleName() + "Array");
                                AIFunctionProperty itemProp = AIFunctionProperty.builder()
                                    .name("item")
                                    .type(mapClassToPropertyType(nestedClass))
                                    .build();
                                nestedSchema.setProperties(List.of(itemProp));
                            } else {
                                nestedSchema.setSchemaName(nestedClass.getSimpleName() + "Array");
                                nestedSchema.setProperties(fromClassRecursive(nestedClass, null).getProperties());
                            }
                            builder.nestedSchema(nestedSchema);
                        }
                    } else {
                        builder.type(PropertyType.OBJECT);
                        builder.nestedSchema(new AIFunctionSchema(rawType.getSimpleName(), new ArrayList<>()));
                    }
                }
            } else {
                builder.type(PropertyType.OBJECT);
            }
        }
        else if (Map.class.isAssignableFrom(type)) {
            builder.type(PropertyType.MAP);
            if (genericType instanceof ParameterizedType paramType) {
                Type keyType = paramType.getActualTypeArguments()[0];
                Type valueType = paramType.getActualTypeArguments()[1];
                
                if (valueType instanceof Class<?> valueClass && !isSimpleType(valueClass)) {
                    AIFunctionSchema valueSchema = fromClassRecursive(valueClass, null);
                    builder.nestedSchema(valueSchema);
                }
                
                Map<String, Object> additionalProps = new HashMap<>();
                additionalProps.put("keyType", keyType.getTypeName());
                additionalProps.put("valueType", valueType.getTypeName());
                builder.additionalProperties(additionalProps);
            }
        }
        else if (isSimpleType(type)) {
            if (builder.type == null) {
                builder.type(mapClassToPropertyType(type));

                if (type.isEnum()) {
                    List<String> enumValues = new ArrayList<>();
                    for (Object enumConstant : type.getEnumConstants()) {
                        enumValues.add(enumConstant.toString());
                    }
                    builder.values(enumValues);

                    SchemaEnumValues enumValuesAnnotation = field.getAnnotation(SchemaEnumValues.class);
                    if (enumValuesAnnotation != null && enumValuesAnnotation.value().length > 0) {
                        builder.values(Arrays.asList(enumValuesAnnotation.value()));
                    }
                }
            }
        }
        else {
            builder.type(PropertyType.OBJECT);
            builder.nestedSchema(fromClassRecursive(type, genericType));
        }
        
        return builder.build();
    }

    private static void setEnumValues(Class<?> type, AIFunctionProperty.AIFunctionPropertyBuilder builder) {
        builder.type(PropertyType.ENUM);
        Object[] enumConstants = type.getEnumConstants();
        List<String> enumValues = new ArrayList<>();
        for (Object constant : enumConstants) {
            enumValues.add(constant.toString());
        }
        builder.values(enumValues);
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() || 
               type.equals(String.class) || 
               type.equals(Integer.class) || 
               type.equals(Double.class) || 
               type.equals(Float.class) || 
               type.equals(Boolean.class) ||
               type.equals(Long.class) ||
               type.equals(Date.class) ||
               type.equals(UUID.class) ||
               type.isEnum();
    }
    
    private static PropertyType mapClassToPropertyType(Class<?> clazz) {
        if (clazz.equals(String.class) || clazz.equals(UUID.class) || clazz.equals(Date.class)) {
            return PropertyType.STRING;
        } else if (clazz.equals(Integer.class) || clazz.equals(int.class) || 
                  clazz.equals(Long.class) || clazz.equals(long.class) ||
                  clazz.equals(Short.class) || clazz.equals(short.class) ||
                  clazz.equals(Byte.class) || clazz.equals(byte.class)) {
            return PropertyType.INTEGER;
        } else if (clazz.equals(Double.class) || clazz.equals(double.class) || 
                  clazz.equals(Float.class) || clazz.equals(float.class)) {
            return PropertyType.DOUBLE;
        } else if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
            return PropertyType.BOOLEAN;
        } else if (clazz.isEnum()) {
            return PropertyType.ENUM;
        } else if (Collection.class.isAssignableFrom(clazz)) {
            return PropertyType.ARRAY;
        } else if (Map.class.isAssignableFrom(clazz)) {
            return PropertyType.MAP;
        } else {
            return PropertyType.OBJECT;
        }
    }
    
    public static void clearCache() {
        schemaCache.clear();
    }

    public enum SchemaGenerationStrategy {
        RECURSIVE,
        JACKSON
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIFunctionProperty implements Serializable {
        
        public static AIFunctionPropertyBuilder builder() {
            return new AIFunctionPropertyBuilder();
        }
        
        public static class AIFunctionPropertyBuilder {
            private String name;
            private String nameId;
            private List<String> nameIds;
            private String description;
            private PropertyType type;
            private String dataNameId;
            private String defaultValue;
            private Integer maxValue;
            private Integer minValue;
            private boolean isRequired;
            private List<String> values;
            private Integer minLength;
            private Integer maxLength;
            private boolean isArray;
            private boolean valueAsNameId;
            private boolean isMultiSelect;
            private AIFunctionSchema nestedSchema;
            private Map<String, Object> additionalProperties;
            
            public AIFunctionPropertyBuilder name(String name) {
                this.name = name;
                return this;
            }
            
            public AIFunctionPropertyBuilder nameId(String nameId) {
                this.nameId = nameId;
                return this;
            }
            
            public AIFunctionPropertyBuilder nameIds(List<String> nameIds) {
                this.nameIds = nameIds;
                return this;
            }
            
            public AIFunctionPropertyBuilder description(String description) {
                this.description = description;
                return this;
            }
            
            public AIFunctionPropertyBuilder type(PropertyType type) {
                this.type = type;
                return this;
            }
            
            public AIFunctionPropertyBuilder dataNameId(String dataNameId) {
                this.dataNameId = dataNameId;
                return this;
            }
            
            public AIFunctionPropertyBuilder defaultValue(String defaultValue) {
                this.defaultValue = defaultValue;
                return this;
            }
            
            public AIFunctionPropertyBuilder maxValue(Integer maxValue) {
                this.maxValue = maxValue;
                return this;
            }
            
            public AIFunctionPropertyBuilder minValue(Integer minValue) {
                this.minValue = minValue;
                return this;
            }
            
            public AIFunctionPropertyBuilder isRequired(boolean isRequired) {
                this.isRequired = isRequired;
                return this;
            }
            
            public AIFunctionPropertyBuilder values(List<String> values) {
                this.values = values;
                return this;
            }
            
            public AIFunctionPropertyBuilder minLength(Integer minLength) {
                this.minLength = minLength;
                return this;
            }
            
            public AIFunctionPropertyBuilder maxLength(Integer maxLength) {
                this.maxLength = maxLength;
                return this;
            }
            
            public AIFunctionPropertyBuilder isArray(boolean isArray) {
                this.isArray = isArray;
                return this;
            }
            
            public AIFunctionPropertyBuilder valueAsNameId(boolean valueAsNameId) {
                this.valueAsNameId = valueAsNameId;
                return this;
            }
            
            public AIFunctionPropertyBuilder isMultiSelect(boolean isMultiSelect) {
                this.isMultiSelect = isMultiSelect;
                return this;
            }
            
            public AIFunctionPropertyBuilder nestedSchema(AIFunctionSchema nestedSchema) {
                this.nestedSchema = nestedSchema;
                return this;
            }
            
            public AIFunctionPropertyBuilder additionalProperties(Map<String, Object> additionalProperties) {
                this.additionalProperties = additionalProperties;
                return this;
            }
            
            public AIFunctionProperty build() {
                AIFunctionProperty property = new AIFunctionProperty();
                property.name = this.name;
                property.nameId = this.nameId;
                property.nameIds = this.nameIds;
                property.description = this.description;
                property.type = this.type;
                property.dataNameId = this.dataNameId;
                property.defaultValue = this.defaultValue;
                property.maxValue = this.maxValue;
                property.minValue = this.minValue;
                property.isRequired = this.isRequired;
                property.values = this.values;
                property.minLength = this.minLength;
                property.maxLength = this.maxLength;
                property.isArray = this.isArray;
                property.valueAsNameId = this.valueAsNameId;
                property.isMultiSelect = this.isMultiSelect;
                property.nestedSchema = this.nestedSchema;
                property.additionalProperties = this.additionalProperties;
                return property;
            }
        }
        private String name;
        private String nameId;
        private List<String> nameIds;
        private String description;
        private PropertyType type;
        private String dataNameId;
        private String defaultValue;
        private Integer maxValue;
        private Integer minValue;
        private boolean isRequired;
        private List<String> values;
        private Integer minLength;
        private Integer maxLength;
        private boolean isArray;
        private boolean valueAsNameId;
        private boolean isMultiSelect;
        private AIFunctionSchema nestedSchema;
        private Map<String, Object> additionalProperties;

        @JsonGetter
        public PropertyType getType() {
            if (CollectionUtils.isNotEmpty(values)) {
                return PropertyType.ENUM;
            }
            return type;
        }

        public void addAdditionalProperty(String key, Object value) {
            if (additionalProperties == null) {
                additionalProperties = new HashMap<>();
            }
            additionalProperties.put(key, value);
        }
    }

    public enum PropertyType {
        STRING,
        INTEGER,
        DOUBLE,
        BOOLEAN,
        LITERAL,
        ENUM,
        OBJECT,
        ARRAY_OBJECT,
        ARRAY,
        MAP
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface SchemaName {
        String value();
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface SchemaDescription {
        String value();
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface SchemaArray {
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface SchemaIgnore {
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface SchemaProperty {
        String nameId() default "";
        String description() default "";
        String defaultValue() default "";
        String dataNameId() default "";
        int minValue() default Integer.MIN_VALUE;
        int maxValue() default Integer.MAX_VALUE;
        int minLength() default 0;
        int maxLength() default 0;
        boolean required() default false;
        boolean multiSelect() default false;
        boolean array() default false;
        boolean valueAsNameId() default false;
        Class<?> type() default Void.class;
        String[] values() default {};
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface SchemaEnumValues {
        String[] value();
    }
}