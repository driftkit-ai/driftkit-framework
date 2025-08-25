package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.StepResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Definition of a mock behavior for a workflow step.
 * Type-safe container for mock configuration.
 * 
 * @param <I> input type
 */
@Getter
@RequiredArgsConstructor
public class MockDefinition<I> {
    
    private final String workflowId;
    private final String stepId;
    private final Class<I> inputType;
    private final MockBehavior<I> behavior;
    
    /**
     * Executes the mock behavior.
     * 
     * @param input the step input
     * @param context the step context
     * @return the mocked step result
     */
    public StepResult<?> execute(Object input, StepContext context) {
        if (!inputType.isInstance(input)) {
            throw new IllegalArgumentException(
                "Mock expects input of type " + inputType.getName() + 
                " but got " + (input != null ? input.getClass().getName() : "null")
            );
        }
        
        @SuppressWarnings("unchecked")
        I typedInput = (I) input;
        
        return behavior.execute(typedInput, context);
    }
    
    /**
     * Creates a mock definition from a simple function.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @param inputType the expected input type
     * @param behavior the mock behavior function
     * @param <I> input type
     * @param <O> output type
     * @return mock definition
     */
    public static <I, O> MockDefinition<I> of(String workflowId, String stepId, 
                                              Class<I> inputType,
                                              Function<I, StepResult<O>> behavior) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(inputType, "inputType cannot be null");
        Objects.requireNonNull(behavior, "behavior cannot be null");
        
        return new MockDefinition<>(
            workflowId, 
            stepId, 
            inputType,
            (input, context) -> behavior.apply(input)
        );
    }
    
    /**
     * Creates a mock definition with context access.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @param inputType the expected input type
     * @param behavior the mock behavior function with context
     * @param <I> input type
     * @param <O> output type
     * @return mock definition
     */
    public static <I, O> MockDefinition<I> ofWithContext(String workflowId, String stepId,
                                                         Class<I> inputType,
                                                         BiFunction<I, StepContext, StepResult<O>> behavior) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(inputType, "inputType cannot be null");
        Objects.requireNonNull(behavior, "behavior cannot be null");
        
        return new MockDefinition<>(
            workflowId,
            stepId,
            inputType,
            behavior::apply
        );
    }
    
    /**
     * Creates a mock that always returns a specific result.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @param inputType the expected input type
     * @param result the result to return
     * @param <I> input type
     * @param <O> output type
     * @return mock definition
     */
    public static <I, O> MockDefinition<I> returning(String workflowId, String stepId,
                                                     Class<I> inputType,
                                                     StepResult<O> result) {
        Objects.requireNonNull(result, "result cannot be null");
        
        return of(workflowId, stepId, inputType, input -> result);
    }
    
    /**
     * Creates a mock that always throws an exception.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @param inputType the expected input type
     * @param exception the exception to throw
     * @param <I> input type
     * @return mock definition
     */
    public static <I> MockDefinition<I> throwing(String workflowId, String stepId,
                                                 Class<I> inputType,
                                                 Exception exception) {
        Objects.requireNonNull(exception, "exception cannot be null");
        
        return of(workflowId, stepId, inputType, input -> {
            throw new RuntimeException(exception);
        });
    }
    
    /**
     * Interface for mock behavior implementation.
     * 
     * @param <I> input type
     */
    @FunctionalInterface
    public interface MockBehavior<I> {
        /**
         * Executes the mock behavior.
         * 
         * @param input the step input
         * @param context the step context
         * @return the mocked step result
         */
        StepResult<?> execute(I input, StepContext context);
    }
}