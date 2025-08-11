package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Definition of a workflow step for the Fluent API.
 * Automatically derives step ID from method reference when possible.
 */
@Slf4j
@Getter
public class StepDefinition {
    
    private final String id;
    private final StepExecutor executor;
    private final String description;
    private final Class<?> inputType;
    private final Class<?> outputType;
    
    private StepDefinition(String id, StepExecutor executor, String description, 
                          Class<?> inputType, Class<?> outputType) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Step executor cannot be null");
        }
        
        this.id = id;
        this.executor = executor;
        this.description = description != null ? description : "Step: " + id;
        this.inputType = inputType != null ? inputType : Object.class;
        this.outputType = outputType != null ? outputType : Object.class;
    }
    
    /**
     * Creates a step definition from a method reference that takes input only.
     * The step ID is automatically derived from the method name.
     * 
     * @param stepFunction Method reference like paymentSteps::validateOrder
     */
    public static <I, O> StepDefinition of(SerializableFunction<I, StepResult<O>> stepFunction) {
        MethodInfo methodInfo = extractMethodInfo(stepFunction);
        return new StepDefinition(
            methodInfo.methodName(),
            (input, context) -> {
                try {
                    return stepFunction.apply((I) input);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(
                        "Input type mismatch for step '" + methodInfo.methodName() + 
                        "'. Expected: " + methodInfo.inputType() + 
                        ", but received: " + (input != null ? input.getClass() : "null"), e);
                }
            },
            null,
            methodInfo.inputType(),
            methodInfo.outputType()
        );
    }
    
    /**
     * Creates a step definition from a method reference that takes input and context.
     * The step ID is automatically derived from the method name.
     * 
     * @param stepFunction Method reference like paymentSteps::processPayment
     */
    public static <I, O> StepDefinition of(SerializableBiFunction<I, WorkflowContext, StepResult<O>> stepFunction) {
        MethodInfo methodInfo = extractMethodInfo(stepFunction);
        return new StepDefinition(
            methodInfo.methodName(),
            (input, context) -> {
                try {
                    return stepFunction.apply((I) input, context);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(
                        "Input type mismatch for step '" + methodInfo.methodName() + 
                        "'. Expected: " + methodInfo.inputType() + 
                        ", but received: " + (input != null ? input.getClass() : "null"), e);
                }
            },
            null,
            methodInfo.inputType(),
            methodInfo.outputType()
        );
    }
    
    /**
     * Creates a step definition with explicit ID (for lambdas).
     * Type information must be provided via withTypes() method.
     * 
     * @param id Explicit step ID
     * @param stepFunction Lambda function
     */
    public static <I, O> StepDefinition of(String id, Function<I, StepResult<O>> stepFunction) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty for lambda functions");
        }
        
        return new StepDefinition(
            id,
            (input, context) -> {
                try {
                    return stepFunction.apply((I) input);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(
                        "Input type mismatch for step '" + id + "'", e);
                }
            },
            null,
            null, // User must specify via withTypes()
            null
        );
    }
    
    /**
     * Creates a step definition with explicit ID and context (for lambdas).
     * Type information must be provided via withTypes() method.
     * 
     * @param id Explicit step ID
     * @param stepFunction Lambda function
     */
    public static <I, O> StepDefinition of(String id, BiFunction<I, WorkflowContext, StepResult<O>> stepFunction) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty for lambda functions");
        }
        
        return new StepDefinition(
            id,
            (input, context) -> {
                try {
                    return stepFunction.apply((I) input, context);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException(
                        "Input type mismatch for step '" + id + "'", e);
                }
            },
            null,
            null, // User must specify via withTypes()
            null
        );
    }
    
    /**
     * Creates a step definition that requires only context (no input).
     * 
     * @param stepFunction Function that takes only WorkflowContext
     */
    public static <O> StepDefinition ofContextOnly(SerializableFunction<WorkflowContext, StepResult<O>> stepFunction) {
        MethodInfo methodInfo = extractMethodInfo(stepFunction);
        return new StepDefinition(
            methodInfo.methodName(),
            (input, context) -> stepFunction.apply(context),
            null,
            Void.class,
            methodInfo.outputType()
        );
    }
    
    /**
     * Creates a step definition that requires only context with explicit ID.
     * Type information for output must be provided via withTypes() method.
     */
    public static <O> StepDefinition ofContextOnly(String id, Function<WorkflowContext, StepResult<O>> stepFunction) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or empty");
        }
        
        return new StepDefinition(
            id,
            (input, context) -> stepFunction.apply(context),
            null,
            Void.class,
            null // User must specify via withTypes()
        );
    }
    
    /**
     * Sets a custom description for this step.
     */
    public StepDefinition withDescription(String description) {
        return new StepDefinition(this.id, this.executor, description, this.inputType, this.outputType);
    }
    
    /**
     * Sets explicit type information for this step.
     * This is required for lambda-based steps where type information cannot be extracted.
     */
    public StepDefinition withTypes(Class<?> inputType, Class<?> outputType) {
        return new StepDefinition(this.id, this.executor, this.description, inputType, outputType);
    }
    
    /**
     * Sets only the input type, keeping the output type as-is.
     */
    public StepDefinition withInputType(Class<?> inputType) {
        return new StepDefinition(this.id, this.executor, this.description, inputType, this.outputType);
    }
    
    /**
     * Sets only the output type, keeping the input type as-is.
     */
    public StepDefinition withOutputType(Class<?> outputType) {
        return new StepDefinition(this.id, this.executor, this.description, this.inputType, outputType);
    }
    
    /**
     * Extracts method information from a serializable lambda using existing reflection.
     */
    private static MethodInfo extractMethodInfo(Serializable lambda) {
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
     * Extracts the inner type from StepResult<T>.
     */
    private static Class<?> extractStepResultType(Type type) {
        if (type instanceof ParameterizedType paramType) {
            Type rawType = paramType.getRawType();
            
            // Handle StepResult<T>
            if (rawType instanceof Class<?> clazz && StepResult.class.isAssignableFrom(clazz)) {
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> resultClass) {
                    return resultClass;
                }
            }
        }
        
        return Object.class;
    }
    
    /**
     * Executor interface for step logic.
     */
    @FunctionalInterface
    public interface StepExecutor {
        StepResult<?> execute(Object input, WorkflowContext context) throws Exception;
    }
    
    /**
     * Serializable version of Function for method reference extraction.
     */
    @FunctionalInterface
    public interface SerializableFunction<T, R> extends Function<T, R>, Serializable {}
    
    /**
     * Serializable version of BiFunction for method reference extraction.
     */
    @FunctionalInterface
    public interface SerializableBiFunction<T, U, R> extends BiFunction<T, U, R>, Serializable {}
    
    /**
     * Information extracted from a method reference.
     */
    private record MethodInfo(
        String methodName,
        Class<?> inputType,
        Class<?> outputType
    ) {}
}