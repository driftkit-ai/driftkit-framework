package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

/**
 * Type-safe wrapper for mock functions that performs runtime type checking.
 * Prevents ClassCastException by validating types before execution.
 * 
 * @param <I> input type
 * @param <O> output type
 */
@Slf4j
@RequiredArgsConstructor
public class TypeSafeFunction<I, O> implements Function<I, StepResult<O>> {
    
    private final Object mockBehavior;
    private final Class<I> inputType;
    private final Class<O> outputType;
    
    @Override
    public StepResult<O> apply(I input) {
        // Validate input type
        if (input != null && !inputType.isInstance(input)) {
            throw new IllegalArgumentException(
                "Mock expects input of type " + inputType.getName() + 
                " but received " + input.getClass().getName()
            );
        }
        
        // Validate mock behavior is a function
        if (!(mockBehavior instanceof Function)) {
            throw new IllegalStateException(
                "Mock behavior must be a Function but was " + 
                (mockBehavior != null ? mockBehavior.getClass().getName() : "null")
            );
        }
        
        try {
            // Execute the mock
            @SuppressWarnings("unchecked")
            Function<I, StepResult<O>> function = (Function<I, StepResult<O>>) mockBehavior;
            StepResult<O> result = function.apply(input);
            
            // Validate result
            if (result == null) {
                throw new IllegalStateException("Mock function returned null result");
            }
            
            // Validate output type if possible
            if (result instanceof StepResult.Continue<O> continueResult && continueResult.data() != null) {
                Object data = continueResult.data();
                if (!outputType.isInstance(data)) {
                    log.warn("Mock returned data of type {} but expected {}", 
                        data.getClass().getName(), outputType.getName());
                }
            }
            
            return result;
            
        } catch (ClassCastException e) {
            throw new IllegalStateException(
                "Type mismatch in mock execution. Check that your mock function has correct type parameters.", e
            );
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Mock execution failed", e);
        }
    }
    
    /**
     * Creates a type-safe function from an untyped object.
     * 
     * @param mockBehavior the mock behavior object
     * @param inputType expected input type
     * @param outputType expected output type
     * @param <I> input type
     * @param <O> output type
     * @return type-safe function
     */
    public static <I, O> TypeSafeFunction<I, O> wrap(Object mockBehavior, 
                                                     Class<I> inputType, 
                                                     Class<O> outputType) {
        return new TypeSafeFunction<>(mockBehavior, inputType, outputType);
    }
    
    /**
     * Creates a type-safe function that validates input type only.
     * Use when output type cannot be determined at compile time.
     * 
     * @param mockBehavior the mock behavior object
     * @param inputType expected input type
     * @param <I> input type
     * @return type-safe function
     */
    public static <I> TypeSafeFunction<I, Object> wrapInput(Object mockBehavior, Class<I> inputType) {
        return new TypeSafeFunction<>(mockBehavior, inputType, Object.class);
    }
}