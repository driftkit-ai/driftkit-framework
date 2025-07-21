package ai.driftkit.common.tools;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.utils.ModelUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing and executing tools (functions) that can be called by language models.
 */
@Slf4j
public class ToolRegistry {
    
    private final Map<String, ToolInfo> tools = new ConcurrentHashMap<>();
    
    /**
     * Registers a single method as a tool function.
     * 
     * @param toolInfo The tool information from ToolAnalyzer
     */
    public void registerTool(ToolInfo toolInfo) {
        String functionName = toolInfo.getFunctionName();
        if (tools.containsKey(functionName)) {
            log.warn("Overwriting existing tool function: {}", functionName);
        }
        
        tools.put(functionName, toolInfo);
        log.info("Registered tool function: {}", functionName);
    }
    
    
    /**
     * Registers a specific method as a tool function.
     * Uses method name as function name.
     * 
     * @param method The method to register
     * @param instance The instance to invoke method on (null for static methods)
     */
    public void registerMethod(Method method, Object instance) {
        registerMethod(method, instance, null);
    }
    
    /**
     * Registers a specific method as a tool function with optional description.
     * 
     * @param method The method to register
     * @param instance The instance to invoke method on (null for static methods)
     * @param description Optional function description (uses method name if null)
     */
    public void registerMethod(Method method, Object instance, String description) {
        String functionName = method.getName();
        if (description == null || description.isEmpty()) {
            description = "Function: " + functionName;
        }
        ToolInfo toolInfo = ToolAnalyzer.analyzeMethod(method, instance, functionName, description);
        registerTool(toolInfo);
    }
    
    /**
     * Registers a specific method by name from a class instance.
     * Uses method name as function name.
     * 
     * @param instance The class instance
     * @param methodName The method name
     */
    public void registerInstanceMethod(Object instance, String methodName) {
        registerInstanceMethod(instance, methodName, null);
    }
    
    /**
     * Registers a specific method by name from a class instance with optional description.
     * 
     * @param instance The class instance
     * @param methodName The method name
     * @param description Optional function description
     */
    public void registerInstanceMethod(Object instance, String methodName, String description) {
        try {
            Method method = findMethodByName(instance.getClass(), methodName);
            registerMethod(method, instance, description);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method not found: " + methodName + " in class " + instance.getClass().getSimpleName(), e);
        }
    }
    
    /**
     * Registers a static method by name from a class.
     * Uses method name as function name.
     * 
     * @param clazz The class
     * @param methodName The method name
     */
    public void registerStaticMethod(Class<?> clazz, String methodName) {
        registerStaticMethod(clazz, methodName, null);
    }
    
    /**
     * Registers a static method by name from a class with optional description.
     * 
     * @param clazz The class
     * @param methodName The method name
     * @param description Optional function description
     */
    public void registerStaticMethod(Class<?> clazz, String methodName, String description) {
        try {
            Method method = findMethodByName(clazz, methodName);
            registerMethod(method, null, description);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Static method not found: " + methodName + " in class " + clazz.getSimpleName(), e);
        }
    }
    
    /**
     * Registers all @ToolFunction annotated methods from a class instance.
     * 
     * @param instance The class instance containing tool methods
     */
    public void registerClass(Object instance) {
        List<ToolInfo> classTools = ToolAnalyzer.analyzeClass(instance.getClass(), instance);
        for (ToolInfo toolInfo : classTools) {
            registerTool(toolInfo);
        }
        log.info("Registered {} tools from class: {}", classTools.size(), instance.getClass().getSimpleName());
    }
    
    /**
     * Registers all static @ToolFunction annotated methods from a class.
     * 
     * @param clazz The class containing static tool methods
     */
    public void registerStaticClass(Class<?> clazz) {
        List<ToolInfo> staticTools = ToolAnalyzer.analyzeStaticMethods(clazz);
        for (ToolInfo toolInfo : staticTools) {
            registerTool(toolInfo);
        }
        log.info("Registered {} static tools from class: {}", staticTools.size(), clazz.getSimpleName());
    }
    
    /**
     * Executes a tool function by name with the provided arguments.
     * 
     * @param functionName Function name
     * @param arguments Function arguments in the order of method parameters
     * @return Function execution result
     */
    public Object executeFunction(String functionName, Object... arguments) {
        ToolInfo toolInfo = tools.get(functionName);
        if (toolInfo == null) {
            throw new RuntimeException("Function not found: " + functionName);
        }
        
        return ToolAnalyzer.invokeToolFunction(toolInfo, arguments);
    }
    
    /**
     * Executes a tool function from a ToolCall with parsed arguments.
     * Converts the Map arguments to typed method parameters.
     * 
     * @param toolCall The tool call with parsed arguments
     * @return Function execution result
     */
    public Object executeToolCall(ToolCall toolCall) {
        String functionName = toolCall.getFunction().getName();
        Map<String, JsonNode> argumentsMap = toolCall.getFunction().getArguments();
        
        ToolInfo toolInfo = tools.get(functionName);
        if (toolInfo == null) {
            throw new RuntimeException("Function not found: " + functionName);
        }
        
        // Check if this is a tool with no method (e.g., AgentAsTool)
        if (toolInfo.getMethod() == null && toolInfo.getInstance() != null) {
            try {
                // For tools without methods, we expect the instance to have an execute method
                // Convert arguments to the tool's argument type
                Object arguments = convertArgumentsToToolType(toolInfo, argumentsMap);
                
                // Use reflection to call execute method
                Type paramType = toolInfo.getParameterTypes().get(0);
                Class<?> paramClass = (paramType instanceof Class<?>) ? (Class<?>) paramType : Object.class;
                Method executeMethod = toolInfo.getInstance().getClass().getMethod("execute", paramClass);
                return executeMethod.invoke(toolInfo.getInstance(), arguments);
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute tool: " + functionName, e);
            }
        } else {
            // Standard method invocation
            Object[] methodArgs = convertArgumentsToArray(toolInfo, argumentsMap);
            return ToolAnalyzer.invokeToolFunction(toolInfo, methodArgs);
        }
    }
    
    /**
     * Converts Map arguments to typed array for method invocation.
     */
    private Object[] convertArgumentsToArray(ToolInfo toolInfo, Map<String, JsonNode> argumentsMap) {
        List<String> parameterNames = toolInfo.getParameterNames();
        List<Type> parameterTypes = toolInfo.getParameterTypes();
        
        Object[] methodArgs = new Object[parameterNames.size()];
        
        for (int i = 0; i < parameterNames.size(); i++) {
            Type type = parameterTypes.get(i);
            String paramName = parameterNames.get(i);
            JsonNode jsonNode = argumentsMap.get(paramName);
            
            if (jsonNode == null || jsonNode.isNull()) {
                methodArgs[i] = null;
            } else {
                try {
                    // Convert JsonNode to the actual parameter type
                    if (type instanceof Class<?>) {
                        Class<?> paramClass = (Class<?>) type;
                        methodArgs[i] = ModelUtils.OBJECT_MAPPER.treeToValue(jsonNode, paramClass);
                    } else {
                        // For generic types, use TypeReference
                        methodArgs[i] = ModelUtils.OBJECT_MAPPER.convertValue(jsonNode, 
                            ModelUtils.OBJECT_MAPPER.constructType(type));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to convert argument '" + paramName + 
                        "' to type " + type.getTypeName() + " for function " + 
                        toolInfo.getFunctionName(), e);
                }
            }
        }
        
        return methodArgs;
    }
    
    /**
     * Converts Map arguments to tool's argument type.
     */
    private Object convertArgumentsToToolType(ToolInfo toolInfo, Map<String, JsonNode> argumentsMap) {
        try {
            // Get the first parameter type which should be the argument type
            if (toolInfo.getParameterTypes() == null || toolInfo.getParameterTypes().isEmpty()) {
                throw new RuntimeException("No parameter types defined for tool: " + toolInfo.getFunctionName());
            }
            
            Type argumentType = toolInfo.getParameterTypes().get(0);
            
            // Convert the entire argumentsMap to the tool's argument type
            if (argumentType instanceof Class<?>) {
                return ModelUtils.OBJECT_MAPPER.convertValue(argumentsMap, (Class<?>) argumentType);
            } else {
                return ModelUtils.OBJECT_MAPPER.convertValue(argumentsMap, 
                    ModelUtils.OBJECT_MAPPER.constructType(argumentType));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert arguments to type " + 
                toolInfo.getParameterTypes().get(0).getTypeName() + " for tool " + toolInfo.getFunctionName(), e);
        }
    }
    
    /**
     * Gets all registered tools as ModelClient.Tool objects for LLM.
     * 
     * @return Array of tool definitions
     */
    public ModelClient.Tool[] getTools() {
        return tools.values().stream()
            .map(ToolInfo::getToolDefinition)
            .toArray(ModelClient.Tool[]::new);
    }
    
    /**
     * Gets tool information by function name.
     * 
     * @param functionName Function name
     * @return ToolInfo or null if not found
     */
    public ToolInfo getToolInfo(String functionName) {
        return tools.get(functionName);
    }
    
    /**
     * Checks if a function is registered.
     * 
     * @param functionName Function name
     * @return true if function exists
     */
    public boolean hasFunction(String functionName) {
        return tools.containsKey(functionName);
    }
    
    /**
     * Removes a tool function from registry.
     * 
     * @param functionName Function name to remove
     * @return true if function was removed
     */
    public boolean removeFunction(String functionName) {
        ToolInfo removed = tools.remove(functionName);
        if (removed != null) {
            log.info("Removed tool function: {}", functionName);
            return true;
        }
        return false;
    }
    
    /**
     * Clears all registered tools.
     */
    public void clearAll() {
        int count = tools.size();
        tools.clear();
        log.info("Cleared {} tool functions from registry", count);
    }
    
    /**
     * Gets the number of registered tools.
     * 
     * @return Number of registered tools
     */
    public int size() {
        return tools.size();
    }
    
    /**
     * Helper method to find a method by name in a class.
     * Handles method overloading by returning the first match.
     */
    private Method findMethodByName(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new NoSuchMethodException("Method " + methodName + " not found in class " + clazz.getSimpleName());
    }
}