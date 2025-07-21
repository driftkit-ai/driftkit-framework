package ai.driftkit.common.tools;

import ai.driftkit.common.domain.client.ModelClient;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analyzes methods and builds tool definitions for language models.
 * Similar to WorkflowAnalyzer but focused on function calling.
 */
@Slf4j
public class ToolAnalyzer {
    
    // Mapping of primitive types to their wrapper classes
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = new HashMap<>();
    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE = new HashMap<>();
    
    static {
        PRIMITIVE_TO_WRAPPER.put(int.class, Integer.class);
        PRIMITIVE_TO_WRAPPER.put(long.class, Long.class);
        PRIMITIVE_TO_WRAPPER.put(double.class, Double.class);
        PRIMITIVE_TO_WRAPPER.put(float.class, Float.class);
        PRIMITIVE_TO_WRAPPER.put(boolean.class, Boolean.class);
        PRIMITIVE_TO_WRAPPER.put(char.class, Character.class);
        PRIMITIVE_TO_WRAPPER.put(byte.class, Byte.class);
        PRIMITIVE_TO_WRAPPER.put(short.class, Short.class);
        
        // Reverse mapping
        for (Map.Entry<Class<?>, Class<?>> entry : PRIMITIVE_TO_WRAPPER.entrySet()) {
            WRAPPER_TO_PRIMITIVE.put(entry.getValue(), entry.getKey());
        }
    }
    
    /**
     * Analyzes a method and creates a ToolInfo with complete signature information.
     * 
     * @param method The method to analyze
     * @param instance The instance to invoke method on (null for static methods)
     * @param functionName Optional function name (uses method name if null)
     * @param description Function description
     * @return ToolInfo with complete method analysis
     */
    public static ToolInfo analyzeMethod(Method method, Object instance, String functionName, String description) {
        if (functionName == null || functionName.isEmpty()) {
            functionName = method.getName();
        }
        
        // Get parameter information
        Parameter[] parameters = method.getParameters();
        List<String> parameterNames = new ArrayList<>();
        List<Type> parameterTypes = new ArrayList<>();
        
        for (Parameter parameter : parameters) {
            parameterNames.add(parameter.getName());
            parameterTypes.add(parameter.getType());
        }
        
        Type returnType = method.getGenericReturnType();
        
        // Generate tool definition
        ModelClient.Tool toolDefinition = generateToolDefinition(functionName, description, parameters);
        
        boolean isStatic = instance == null;
        
        return new ToolInfo(
            functionName,
            description,
            method,
            instance,
            parameterNames,
            parameterTypes,
            returnType,
            isStatic,
            toolDefinition
        );
    }
    
    /**
     * Analyzes all methods with @ToolFunction annotation in a class.
     * Supports both static methods and instance methods.
     * 
     * @param clazz The class to analyze
     * @param instance The instance (null for static methods only)
     * @return List of ToolInfo for annotated methods
     */
    public static List<ToolInfo> analyzeClass(Class<?> clazz, Object instance) {
        List<ToolInfo> tools = new ArrayList<>();
        
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            // Only process methods with @ToolFunction annotation
            if (method.isAnnotationPresent(Tool.class)) {
                Tool annotation = method.getAnnotation(Tool.class);
                String functionName = annotation.name().isEmpty() ? method.getName() : annotation.name();
                String description = annotation.description();
                
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                Object methodInstance = isStatic ? null : instance;
                
                // Validate that we have instance for non-static methods
                if (!isStatic && instance == null) {
                    log.warn("Skipping non-static method {} because no instance provided", method.getName());
                    continue;
                }
                
                ToolInfo toolInfo = analyzeMethod(method, methodInstance, functionName, description);
                tools.add(toolInfo);
                
                log.debug("Found {} tool function: {} in class {}", 
                    isStatic ? "static" : "instance", functionName, clazz.getSimpleName());
            }
        }
        
        return tools;
    }
    
    /**
     * Analyzes only static methods with @ToolFunction annotation in a class.
     * 
     * @param clazz The class to analyze
     * @return List of ToolInfo for static annotated methods
     */
    public static List<ToolInfo> analyzeStaticMethods(Class<?> clazz) {
        return analyzeClass(clazz, null);
    }
    
    /**
     * Generates a ModelClient.Tool definition from method parameters.
     */
    private static ModelClient.Tool generateToolDefinition(String functionName, String description, Parameter[] parameters) {
        ModelClient.Tool tool = new ModelClient.Tool();
        tool.setType(ModelClient.ResponseFormatType.function);
        
        ModelClient.ToolFunction toolFunction = new ModelClient.ToolFunction();
        toolFunction.setName(functionName);
        toolFunction.setDescription(description);
        
        // Create parameters schema
        ModelClient.ToolFunction.FunctionParameters functionParameters = new ModelClient.ToolFunction.FunctionParameters();
        functionParameters.setType(ModelClient.ResponseFormatType.Object);
        
        Map<String, ModelClient.Property> properties = new ConcurrentHashMap<>();
        List<String> required = new ArrayList<>();
        
        // Process each parameter
        for (Parameter parameter : parameters) {
            String paramName = parameter.getName();
            Class<?> paramType = parameter.getType();
            
            ModelClient.Property property = createPropertyFromType(paramType);
            properties.put(paramName, property);
            
            // All parameters are required by default
            required.add(paramName);
        }
        
        functionParameters.setProperties(properties);
        functionParameters.setRequired(required);
        
        toolFunction.setParameters(functionParameters);
        tool.setFunction(toolFunction);
        
        log.debug("Generated tool definition for function: {}", functionName);
        return tool;
    }
    
    /**
     * Creates a ModelClient.Property from a Java type.
     * Supports recursive parsing for complex objects.
     */
    private static ModelClient.Property createPropertyFromType(Class<?> type) {
        return createPropertyFromType(type, new HashSet<>());
    }
    
    /**
     * Creates a ModelClient.Property from a Java type with cycle detection.
     */
    private static ModelClient.Property createPropertyFromType(Class<?> type, Set<Class<?>> visitedTypes) {
        ModelClient.Property property = new ModelClient.Property();
        
        if (String.class.equals(type)) {
            property.setType(ModelClient.ResponseFormatType.String);
        } else if (Integer.class.equals(type) || int.class.equals(type) ||
                   Long.class.equals(type) || long.class.equals(type)) {
            property.setType(ModelClient.ResponseFormatType.Integer);
        } else if (Double.class.equals(type) || double.class.equals(type) ||
                   Float.class.equals(type) || float.class.equals(type)) {
            property.setType(ModelClient.ResponseFormatType.Number);
        } else if (Boolean.class.equals(type) || boolean.class.equals(type)) {
            property.setType(ModelClient.ResponseFormatType.Boolean);
        } else if (type.isEnum()) {
            property.setType(ModelClient.ResponseFormatType.String);
            
            // Add enum values
            List<String> enumValues = new ArrayList<>();
            for (Object enumConstant : type.getEnumConstants()) {
                enumValues.add(enumConstant.toString());
            }
            property.setEnumValues(enumValues);
        } else if (type.isArray()) {
            property.setType(ModelClient.ResponseFormatType.Array);
            Class<?> componentType = type.getComponentType();
            ModelClient.Property itemProperty = createPropertyFromType(componentType, visitedTypes);
            property.setItems(itemProperty);
        } else if (Collection.class.isAssignableFrom(type)) {
            property.setType(ModelClient.ResponseFormatType.Array);
            // For collections, we can't determine the generic type at runtime without additional info
            ModelClient.Property itemProperty = new ModelClient.Property();
            itemProperty.setType(ModelClient.ResponseFormatType.Object);
            property.setItems(itemProperty);
        } else if (Map.class.isAssignableFrom(type)) {
            property.setType(ModelClient.ResponseFormatType.Object);
            property.setDescription("Map with string keys");
        } else {
            // For complex objects, recursively parse fields
            property.setType(ModelClient.ResponseFormatType.Object);
            
            // Prevent infinite recursion
            if (visitedTypes.contains(type)) {
                property.setDescription("Recursive reference to " + type.getSimpleName());
                return property;
            }
            
            visitedTypes.add(type);
            
            Map<String, ModelClient.Property> properties = new HashMap<>();
            
            // Parse all declared fields
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                // Skip static and transient fields
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();
                
                ModelClient.Property fieldProperty = createPropertyFromType(fieldType, new HashSet<>(visitedTypes));
                properties.put(fieldName, fieldProperty);
            }
            
            property.setProperties(properties);
            // Don't set required for nested object fields - only top-level parameters are required by default
            property.setDescription(type.getSimpleName());
        }
        
        return property;
    }
    
    /**
     * Invokes a tool function with strongly typed arguments.
     * Handles both static and instance methods.
     * 
     * @param toolInfo The tool information
     * @param arguments Arguments in the order of method parameters
     * @return Method execution result
     */
    public static Object invokeToolFunction(ToolInfo toolInfo, Object... arguments) {
        try {
            Method method = toolInfo.getMethod();
            Object instance = toolInfo.getInstance();
            
            // Validate argument count
            if (arguments.length != toolInfo.getParameterTypes().size()) {
                throw new IllegalArgumentException("Argument count mismatch for function " + 
                    toolInfo.getFunctionName() + ": expected " + toolInfo.getParameterTypes().size() + 
                    ", got " + arguments.length);
            }
            
            // Type check arguments
            for (int i = 0; i < arguments.length; i++) {
                Type expectedType = toolInfo.getParameterTypes().get(i);
                Object arg = arguments[i];
                
                if (arg != null && expectedType instanceof Class<?>) {
                    Class<?> expectedClass = (Class<?>) expectedType;
                    if (!isAssignableFrom(expectedClass, arg.getClass())) {
                        throw new IllegalArgumentException("Type mismatch for parameter " + i + 
                            " in function " + toolInfo.getFunctionName() + 
                            ": expected " + expectedClass.getSimpleName() + 
                            ", got " + arg.getClass().getSimpleName());
                    }
                }
            }
            
            // Invoke method (instance will be null for static methods)
            return method.invoke(instance, arguments);
            
        } catch (Exception e) {
            log.error("Error invoking tool function {}: {}", toolInfo.getFunctionName(), e.getMessage(), e);
            throw new RuntimeException("Tool function execution failed: " + toolInfo.getFunctionName(), e);
        }
    }
    
    /**
     * Checks if a class is assignable from another class, handling primitives.
     */
    private static boolean isAssignableFrom(Class<?> expected, Class<?> actual) {
        if (expected.isAssignableFrom(actual)) {
            return true;
        }
        
        // Check primitive to wrapper conversions
        Class<?> wrapper = PRIMITIVE_TO_WRAPPER.get(expected);
        if (wrapper != null && wrapper.equals(actual)) {
            return true;
        }
        
        // Check wrapper to primitive conversions
        Class<?> primitive = WRAPPER_TO_PRIMITIVE.get(expected);
        if (primitive != null && primitive.equals(actual)) {
            return true;
        }
        
        return false;
    }
}