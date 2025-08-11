package ai.driftkit.workflow.engine.analyzer;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Analyzes method signatures and return types for workflow steps.
 * Extracts type information and validates method signatures.
 */
@Slf4j
@UtilityClass
public class MethodAnalyzer {
    
    /**
     * Validates that a step method has a valid signature.
     * 
     * @param method The method to validate
     * @throws IllegalArgumentException if the method signature is invalid
     */
    public static void validateStepMethod(Method method) {
        Class<?> returnType = method.getReturnType();
        
        // Check return type
        if (!StepResult.class.isAssignableFrom(returnType) && 
            !CompletableFuture.class.isAssignableFrom(returnType)) {
            throw new IllegalArgumentException(
                "Step method must return StepResult or CompletableFuture<StepResult>: " + 
                method.getName() + " returns " + returnType.getName()
            );
        }
        
        // Validate CompletableFuture generic type
        if (CompletableFuture.class.isAssignableFrom(returnType)) {
            Type genericReturnType = method.getGenericReturnType();
            if (!(genericReturnType instanceof ParameterizedType pt)) {
                throw new IllegalArgumentException(
                    "CompletableFuture must be parameterized: " + method.getName()
                );
            }
            
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            if (actualTypeArguments.length == 0) {
                throw new IllegalArgumentException(
                    "CompletableFuture must have type parameter: " + method.getName()
                );
            }
            
            Type actualType = actualTypeArguments[0];
            if (!isStepResultType(actualType)) {
                throw new IllegalArgumentException(
                    "CompletableFuture must contain StepResult type: " + method.getName() + 
                    " contains " + actualType.getTypeName()
                );
            }
        }
        
        // Validate parameter count
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 2) {
            throw new IllegalArgumentException(
                "Step method can have at most 2 parameters (input and context): " + 
                method.getName() + " has " + paramTypes.length
            );
        }
        
        // Validate parameter types
        int contextParams = 0;
        int inputParams = 0;
        for (Class<?> paramType : paramTypes) {
            if (WorkflowContext.class.isAssignableFrom(paramType)) {
                contextParams++;
            } else {
                inputParams++;
            }
        }
        
        if (contextParams > 1) {
            throw new IllegalArgumentException(
                "Step method can have at most one WorkflowContext parameter: " + method.getName()
            );
        }
        
        if (inputParams > 1) {
            throw new IllegalArgumentException(
                "Step method can have at most one input parameter: " + method.getName()
            );
        }
    }
    
    /**
     * Analyzes method parameters to populate StepInfo.
     * 
     * @param stepInfo The StepInfo to populate
     */
    public static void analyzeMethodParameters(StepInfo stepInfo) {
        Method method = stepInfo.getMethod();
        Class<?>[] paramTypes = method.getParameterTypes();
        Type[] genericParamTypes = method.getGenericParameterTypes();
        
        stepInfo.setRequiresContext(false);
        stepInfo.setInputType(null);
        
        // Process parameters in order
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            
            if (WorkflowContext.class.isAssignableFrom(paramType)) {
                stepInfo.setRequiresContext(true);
                stepInfo.setContextParameterIndex(i);
            } else {
                // This is the input parameter
                stepInfo.setInputType(paramType);
                stepInfo.setInputParameterIndex(i);
                
                // Store generic type information if available
                if (genericParamTypes[i] instanceof ParameterizedType pt) {
                    stepInfo.setGenericInputType(pt);
                }
            }
        }
        
        // If no input type found, default to Object
        if (stepInfo.getInputType() == null) {
            stepInfo.setInputType(Object.class);
            log.debug("Step {} has no input parameter, defaulting to Object type", stepInfo.getId());
        }
    }
    
    /**
     * Analyzes the return type to determine possible next steps.
     * 
     * @param stepInfo The StepInfo to populate with return type information
     */
    public static void analyzeReturnType(StepInfo stepInfo) {
        Method method = stepInfo.getMethod();
        Type returnType = method.getGenericReturnType();
        
        // Handle CompletableFuture unwrapping
        if (returnType instanceof ParameterizedType pt && 
            CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
            returnType = pt.getActualTypeArguments()[0];
        }
        
        // Extract the inner type from StepResult<T>
        if (!(returnType instanceof ParameterizedType pt) || !isStepResultType(pt.getRawType())) {
            // Raw StepResult without type parameter
            log.warn("Step {} returns raw StepResult without type parameter. " +
                "Consider adding type parameter for better type safety.", stepInfo.getId());
            stepInfo.setPossibleContinueType(Object.class);
            return;
        }
        
        Type[] typeArgs = pt.getActualTypeArguments();
        if (typeArgs.length == 0) {
            return;
        }
        
        Type innerType = typeArgs[0];
        
        // Handle wildcards
        if (innerType instanceof WildcardType wt) {
            Type[] upperBounds = wt.getUpperBounds();
            if (upperBounds.length > 0) {
                innerType = upperBounds[0];
            }
        }
        
        // Check if it's a sealed interface or class
        if (innerType instanceof Class<?> clazz) {
            if (clazz.isSealed()) {
                // Get all permitted subclasses
                Class<?>[] permitted = clazz.getPermittedSubclasses();
                stepInfo.getPossibleBranchTypes().addAll(Arrays.asList(permitted));
                log.debug("Step {} can branch to {} types via sealed interface {}", 
                    stepInfo.getId(), permitted.length, clazz.getSimpleName());
            } else if (!clazz.equals(Void.class) && !clazz.equals(void.class)) {
                // Single concrete type for Continue
                stepInfo.setPossibleContinueType(clazz);
                log.debug("Step {} continues with type {}", 
                    stepInfo.getId(), clazz.getSimpleName());
            }
        }
        
        // Store the complete return type info
        stepInfo.setReturnTypeInfo(new StepInfo.ReturnTypeInfo(pt, innerType));
    }
    
    /**
     * Checks if a type represents StepResult.
     */
    private static boolean isStepResultType(Type type) {
        if (type instanceof Class<?> clazz) {
            return StepResult.class.isAssignableFrom(clazz);
        }
        if (type instanceof ParameterizedType pt) {
            return isStepResultType(pt.getRawType());
        }
        return false;
    }
    
    /**
     * Extracts the inner type from StepResult<T> or CompletableFuture<StepResult<T>>.
     * 
     * @param type The generic type to extract from
     * @return The inner type class, or Object.class if it cannot be determined
     */
    public static Class<?> extractStepResultType(Type type) {
        if (!(type instanceof ParameterizedType paramType)) {
            return Object.class;
        }
        
        Type rawType = paramType.getRawType();
        
        // Handle CompletableFuture<StepResult<T>>
        if (rawType instanceof Class<?> clazz && CompletableFuture.class.isAssignableFrom(clazz)) {
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof ParameterizedType innerType) {
                return extractStepResultType(innerType);
            }
        }
        
        // Handle StepResult<T>
        if (rawType instanceof Class<?> clazz && StepResult.class.isAssignableFrom(clazz)) {
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> resultClass) {
                return resultClass;
            }
        }
        
        return Object.class;
    }
}