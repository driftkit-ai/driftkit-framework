package ai.driftkit.workflow.engine.utils;

import ai.driftkit.workflow.engine.async.TaskProgressReporter;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for common reflection operations used throughout the workflow engine.
 * Extracted from WorkflowBuilder, StepDefinition, and AsyncStepHandler to centralize reflection logic.
 */
@Slf4j
public final class ReflectionUtils {
    
    private static final AtomicInteger STEP_COUNTER = new AtomicInteger(0);
    
    private ReflectionUtils() {}
    
    /**
     * Extracts method name from a lambda expression or method reference.
     * This is the exact logic from WorkflowBuilder.extractLambdaMethodName().
     * 
     * @param lambda The lambda or method reference
     * @return The extracted method name or generated ID
     */
    public static String extractLambdaMethodName(Object lambda) {
        if (lambda == null) {
            throw new IllegalArgumentException("Lambda cannot be null");
        }
        
        // Try SerializedLambda approach first (works when lambda is Serializable)
        if (lambda instanceof Serializable) {
            try {
                Method writeReplace = lambda.getClass().getDeclaredMethod("writeReplace");
                writeReplace.setAccessible(true);
                SerializedLambda serializedLambda = (SerializedLambda) writeReplace.invoke(lambda);
                
                String implMethodName = serializedLambda.getImplMethodName();
                
                // Check if it's a synthetic lambda (starts with "lambda$")
                if (implMethodName.startsWith("lambda$")) {
                    // Generate a more meaningful ID
                    return generateStepId();
                }
                
                // It's a method reference, return the method name
                log.debug("Extracted method name from SerializedLambda: {}", implMethodName);
                return implMethodName;
                
            } catch (Exception e) {
                log.debug("Could not extract method name from SerializedLambda: {}", e.getMessage());
            }
        }
        
        // Fallback: try to extract from class name
        String className = lambda.getClass().getName();
        
        if (className.contains("$$Lambda$")) {
            // Extract the base class name as a hint
            int lambdaIndex = className.indexOf("$$Lambda$");
            String baseClass = className.substring(0, lambdaIndex);
            int lastDot = baseClass.lastIndexOf('.');
            if (lastDot >= 0) {
                baseClass = baseClass.substring(lastDot + 1);
            }
            return baseClass.toLowerCase() + "_" + generateStepId().substring(5); // Remove "step_" prefix
        }
        
        // Ultimate fallback
        return generateStepId();
    }
    
    /**
     * Generate a unique step ID.
     * This is the exact logic from WorkflowBuilder.generateStepId().
     * 
     * @return A unique step ID
     */
    public static String generateStepId() {
        return "step_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Extracts method information from a serializable lambda.
     * This is the exact logic from StepDefinition.extractMethodInfo().
     * 
     * @param lambda The serializable lambda
     * @return MethodInfo containing method details
     */
    public static MethodInfo extractMethodInfo(Serializable lambda) {
        try {
            Method writeReplace = lambda.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda serializedLambda = (SerializedLambda) writeReplace.invoke(lambda);
            
            String implClass = serializedLambda.getImplClass().replace('/', '.');
            String implMethodName = serializedLambda.getImplMethodName();
            
            // Load the class and find the method
            Class<?> clazz = Class.forName(implClass);
            
            // Find the method - we need to search through all methods
            // since we don't know the exact parameter types
            Method targetMethod = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(implMethodName)) {
                    targetMethod = method;
                    break;
                }
            }
            
            if (targetMethod == null) {
                throw new IllegalStateException("Could not find method: " + implMethodName);
            }
            
            // Extract input type (first parameter that's not WorkflowContext)
            Class<?> inputType = null;
            for (Class<?> paramType : targetMethod.getParameterTypes()) {
                if (!WorkflowContext.class.isAssignableFrom(paramType)) {
                    inputType = paramType;
                    break;
                }
            }
            
            // Extract output type from StepResult<T>
            Class<?> outputType = extractStepResultType(targetMethod.getGenericReturnType());
            
            log.debug("Extracted method info: class={}, method={}, input={}, output={}", 
                implClass, implMethodName, inputType, outputType);
            
            return new MethodInfo(implMethodName, inputType, outputType);
            
        } catch (Exception e) {
            log.error("Failed to extract method info from lambda", e);
            throw new IllegalArgumentException(
                "Cannot extract method name from lambda. Please use explicit ID.", e);
        }
    }
    
    /**
     * Builds method arguments for async step invocation.
     * This is the exact logic from AsyncStepHandler.buildAsyncMethodArgs().
     * 
     * @param method The method to invoke
     * @param asyncResult The async result object
     * @param context The workflow context
     * @param progressReporter The progress reporter
     * @return Array of method arguments in correct order
     */
    public static Object[] buildAsyncMethodArgs(Method method, Object asyncResult, 
                                              WorkflowContext context, TaskProgressReporter progressReporter) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        
        boolean hasTaskArgs = false;
        boolean hasContext = false;
        boolean hasProgress = false;
        
        // Fill arguments based on parameter types
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            
            if (!hasTaskArgs && (Map.class.isAssignableFrom(paramType) || paramType.isInstance(asyncResult))) {
                args[i] = asyncResult;
                hasTaskArgs = true;
            } else if (!hasContext && WorkflowContext.class.isAssignableFrom(paramType)) {
                args[i] = context;
                hasContext = true;
            } else if (!hasProgress && TaskProgressReporter.class.isAssignableFrom(paramType)) {
                args[i] = progressReporter;
                hasProgress = true;
            } else {
                throw new IllegalArgumentException(
                    "Async method " + method.getName() + " has unexpected parameter type at position " + i + 
                    ": " + paramType.getName()
                );
            }
        }
        
        // Validate that all required parameters are present
        if (!hasProgress) {
            throw new IllegalArgumentException(
                "Async method " + method.getName() + " must accept TaskProgressReporter parameter"
            );
        }
        
        return args;
    }
    
    /**
     * Extracts the generic type parameter from StepResult<T>.
     * This is the exact logic from StepDefinition.extractStepResultType().
     * 
     * @param returnType The return type to analyze
     * @return The extracted type or Object.class if not found
     */
    public static Class<?> extractStepResultType(Type returnType) {
        if (returnType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) returnType;
            
            // Check if it's StepResult<T>
            if (paramType.getRawType() == StepResult.class) {
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    return (Class<?>) typeArgs[0];
                }
            }
        }
        
        // Default to Object if we can't determine the type
        return Object.class;
    }
    
    /**
     * Container for method reflection information.
     * This matches the MethodInfo class from StepDefinition.
     */
    public static class MethodInfo {
        private final String methodName;
        private final Class<?> inputType;
        private final Class<?> outputType;
        
        public MethodInfo(String methodName, Class<?> inputType, Class<?> outputType) {
            this.methodName = methodName;
            this.inputType = inputType;
            this.outputType = outputType;
        }
        
        public String getMethodName() { return methodName; }
        public Class<?> getInputType() { return inputType; }
        public Class<?> getOutputType() { return outputType; }
        
        @Override
        public String toString() {
            return methodName + "(" + 
                   (inputType != null ? inputType.getSimpleName() : "?") + 
                   ") -> " + 
                   (outputType != null ? outputType.getSimpleName() : "?");
        }
    }
}