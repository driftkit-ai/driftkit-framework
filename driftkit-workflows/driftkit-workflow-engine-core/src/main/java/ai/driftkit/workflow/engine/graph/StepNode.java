package ai.driftkit.workflow.engine.graph;

import ai.driftkit.workflow.engine.annotations.OnInvocationsLimit;
import ai.driftkit.workflow.engine.annotations.RetryPolicy;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Represents a node in the workflow graph.
 * Each node encapsulates the metadata and execution logic for a single workflow step.
 */
public record StepNode(
    String id,
    String description,
    StepExecutor executor,
    boolean isAsync,
    boolean isInitial,
    RetryPolicy retryPolicy,
    int invocationLimit,
    OnInvocationsLimit onInvocationsLimit
) {
    /**
     * Validates the StepNode parameters.
     */
    public StepNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step ID cannot be null or blank");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Step executor cannot be null");
        }
        if (description == null || description.isBlank()) {
            description = "Step: " + id;
        }
        // Set defaults for retry configuration
        if (invocationLimit <= 0) {
            invocationLimit = 100;
        }
        if (onInvocationsLimit == null) {
            onInvocationsLimit = OnInvocationsLimit.ERROR;
        }
        // retryPolicy can be null - means no retry
    }
    
    /**
     * Checks if this step can accept the given input type.
     */
    public boolean canAcceptInput(Class<?> inputType) {
        Class<?> expectedType = executor.getInputType();
        if (expectedType == null) {
            // Step doesn't require input - only initial steps should accept any input
            return isInitial;
        }
        return expectedType.isAssignableFrom(inputType);
    }
    
    /**
     * Factory method to create a StepNode from a method reference.
     */
    public static StepNode fromMethod(String id, Method method, Object instance) {
        String desc = "Execute " + method.getName();
        boolean async = CompletableFuture.class.isAssignableFrom(method.getReturnType());
        return new StepNode(id, desc, new MethodStepExecutor(method, instance), async, false, 
                           null, 100, OnInvocationsLimit.ERROR);
    }
    
    /**
     * Factory method to create a StepNode from a method reference with retry configuration.
     */
    public static StepNode fromMethod(String id, Method method, Object instance,
                                     RetryPolicy retryPolicy, int invocationLimit, 
                                     OnInvocationsLimit onInvocationsLimit) {
        String desc = "Execute " + method.getName();
        boolean async = CompletableFuture.class.isAssignableFrom(method.getReturnType());
        return new StepNode(id, desc, new MethodStepExecutor(method, instance), async, false,
                           retryPolicy, invocationLimit, onInvocationsLimit);
    }
    
    /**
     * Factory method to create a StepNode from a function.
     */
    public static StepNode fromFunction(String id, Function<Object, StepResult<?>> function) {
        return new StepNode(id, "Function step", new FunctionStepExecutor(function, null, null), false, false,
                           null, 100, OnInvocationsLimit.ERROR);
    }
    
    /**
     * Factory method to create a StepNode from a function with explicit type information.
     */
    public static <I, O> StepNode fromFunction(String id, 
                                               Function<Object, StepResult<?>> function,
                                               Class<I> inputType,
                                               Class<O> outputType) {
        return new StepNode(id, "Function step", 
            new FunctionStepExecutor(function, inputType, outputType), false, false,
            null, 100, OnInvocationsLimit.ERROR);
    }
    
    /**
     * Factory method to create a StepNode from a bi-function that accepts context.
     */
    public static StepNode fromBiFunction(String id, BiFunction<Object, WorkflowContext, StepResult<?>> function) {
        return new StepNode(id, "BiFunction step", new BiFunctionStepExecutor(function, null, null), false, false,
                           null, 100, OnInvocationsLimit.ERROR);
    }
    
    /**
     * Factory method to create a StepNode from a bi-function with explicit type information.
     */
    public static <I, O> StepNode fromBiFunction(String id, 
                                                 BiFunction<Object, WorkflowContext, StepResult<?>> function,
                                                 Class<I> inputType,
                                                 Class<O> outputType) {
        return new StepNode(id, "BiFunction step", 
            new BiFunctionStepExecutor(function, inputType, outputType), false, false,
            null, 100, OnInvocationsLimit.ERROR);
    }
    
    /**
     * Creates a new StepNode with the initial flag set.
     */
    public StepNode asInitial() {
        return new StepNode(id, description, executor, isAsync, true, retryPolicy, invocationLimit, onInvocationsLimit);
    }
    
    /**
     * Creates a new StepNode with the async flag set.
     */
    public StepNode asAsync() {
        return new StepNode(id, description, executor, true, isInitial, retryPolicy, invocationLimit, onInvocationsLimit);
    }
    
    /**
     * Creates a new StepNode with a different description.
     */
    public StepNode withDescription(String newDescription) {
        return new StepNode(id, newDescription, executor, isAsync, isInitial, retryPolicy, invocationLimit, onInvocationsLimit);
    }
    
    /**
     * Creates a new StepNode with retry configuration.
     */
    public StepNode withRetry(RetryPolicy retryPolicy, int invocationLimit, OnInvocationsLimit onInvocationsLimit) {
        return new StepNode(id, description, executor, isAsync, isInitial, retryPolicy, invocationLimit, onInvocationsLimit);
    }
    
    /**
     * Interface for step execution strategies.
     */
    public interface StepExecutor {
        /**
         * Executes the step logic.
         * 
         * @param input The input data for the step
         * @param context The workflow context
         * @return The result of the step execution
         * @throws Exception if execution fails
         */
        Object execute(Object input, WorkflowContext context) throws Exception;
        
        /**
         * Gets the expected input type for this step.
         * 
         * @return The input type class, or null if any type is accepted
         */
        Class<?> getInputType();
        
        /**
         * Gets the output type this step produces.
         * This is the type wrapped in StepResult (e.g., for StepResult<String>, returns String.class)
         * 
         * @return The output type class, or null if unknown
         */
        Class<?> getOutputType();
        
        /**
         * Checks if this executor requires the workflow context as a parameter.
         * 
         * @return true if context is required
         */
        boolean requiresContext();
    }
    
    /**
     * Executor implementation for method-based steps.
     */
    private record MethodStepExecutor(Method method, Object instance) implements StepExecutor {
        @Override
        public Object execute(Object input, WorkflowContext context) throws Exception {
            Class<?>[] paramTypes = method.getParameterTypes();
            
            // Build arguments array based on parameter types
            Object[] args = new Object[paramTypes.length];
            
            // Track which arguments we've filled
            boolean contextUsed = false;
            boolean inputUsed = false;
            
            // First pass: fill exact type matches
            for (int i = 0; i < paramTypes.length; i++) {
                if (!contextUsed && WorkflowContext.class.isAssignableFrom(paramTypes[i])) {
                    args[i] = context;
                    contextUsed = true;
                } else if (!inputUsed && input != null && paramTypes[i].isInstance(input)) {
                    args[i] = input;
                    inputUsed = true;
                }
            }
            
            // Second pass: fill remaining slots
            for (int i = 0; i < paramTypes.length; i++) {
                if (args[i] == null) {
                    // Try to use input if not used yet and type is not WorkflowContext
                    if (!inputUsed && !WorkflowContext.class.isAssignableFrom(paramTypes[i])) {
                        if (input == null && !paramTypes[i].isPrimitive()) {
                            // Null is acceptable for non-primitive types
                            args[i] = null;
                            inputUsed = true;
                        } else if (input != null) {
                            // Validate type compatibility
                            if (!paramTypes[i].isInstance(input)) {
                                throw new IllegalArgumentException(String.format(
                                    "Step '%s' parameter %d expects type %s but received %s",
                                    method.getName(), i, paramTypes[i].getName(), input.getClass().getName()
                                ));
                            }
                            args[i] = input;
                            inputUsed = true;
                        } else {
                            throw new IllegalArgumentException(String.format(
                                "Step '%s' parameter %d of primitive type %s cannot be null",
                                method.getName(), i, paramTypes[i].getName()
                            ));
                        }
                    }
                }
            }
            
            // Validate all parameters are filled
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null && paramTypes[i].isPrimitive()) {
                    throw new IllegalArgumentException(String.format(
                        "Step '%s' parameter %d of primitive type %s was not provided",
                        method.getName(), i, paramTypes[i].getName()
                    ));
                }
            }
            
            return method.invoke(instance, args);
        }
        
        @Override
        public Class<?> getInputType() {
            Class<?>[] paramTypes = method.getParameterTypes();
            
            // Find the first parameter that is not WorkflowContext
            for (Class<?> paramType : paramTypes) {
                if (!WorkflowContext.class.isAssignableFrom(paramType)) {
                    return paramType;
                }
            }
            
            // No input parameter found
            return null;
        }
        
        @Override
        public Class<?> getOutputType() {
            Type genericReturnType = method.getGenericReturnType();
            return extractStepResultType(genericReturnType);
        }
        
        @Override
        public boolean requiresContext() {
            Class<?>[] paramTypes = method.getParameterTypes();
            for (Class<?> paramType : paramTypes) {
                if (WorkflowContext.class.isAssignableFrom(paramType)) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * Extracts the inner type from StepResult<T> or CompletableFuture<StepResult<T>>
         */
        private Class<?> extractStepResultType(Type type) {
            if (type instanceof ParameterizedType paramType) {
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
            }
            
            return Object.class;
        }
    }
    
    /**
     * Executor implementation for function-based steps.
     */
    private record FunctionStepExecutor(
        Function<Object, StepResult<?>> function,
        Class<?> inputType,
        Class<?> outputType
    ) implements StepExecutor {
        @Override
        public Object execute(Object input, WorkflowContext context) {
            return function.apply(input);
        }
        
        @Override
        public Class<?> getInputType() {
            return inputType;
        }
        
        @Override
        public Class<?> getOutputType() {
            return outputType;
        }
        
        @Override
        public boolean requiresContext() {
            return false;
        }
    }
    
    /**
     * Executor implementation for bi-function-based steps that accept context.
     */
    private record BiFunctionStepExecutor(
        BiFunction<Object, WorkflowContext, StepResult<?>> function,
        Class<?> inputType,
        Class<?> outputType
    ) implements StepExecutor {
        @Override
        public Object execute(Object input, WorkflowContext context) {
            return function.apply(input, context);
        }
        
        @Override
        public Class<?> getInputType() {
            return inputType;
        }
        
        @Override
        public Class<?> getOutputType() {
            return outputType;
        }
        
        @Override
        public boolean requiresContext() {
            return true;
        }
    }
}