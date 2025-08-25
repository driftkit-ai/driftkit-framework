package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.StepResult;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent builder for creating mock definitions.
 * Provides a clean API for configuring workflow step mocks.
 */
public class MockBuilder {
    
    private final MockRegistry registry;
    
    MockBuilder(MockRegistry registry) {
        this.registry = registry;
    }
    
    /**
     * Starts building a mock for a workflow.
     * 
     * @param workflowId the workflow ID
     * @return workflow mock builder
     */
    public WorkflowMockBuilder workflow(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        return new WorkflowMockBuilder(registry, workflowId);
    }
    
    /**
     * Builder for workflow-specific mocks.
     */
    @RequiredArgsConstructor
    public static class WorkflowMockBuilder {
        private final MockRegistry registry;
        private final String workflowId;
        
        /**
         * Starts building a mock for a specific step.
         * 
         * @param stepId the step ID
         * @return step mock builder
         */
        public StepMockBuilder step(String stepId) {
            Objects.requireNonNull(stepId, "stepId cannot be null");
            return new StepMockBuilder(registry, workflowId, stepId);
        }
    }
    
    /**
     * Builder for step-specific mocks.
     */
    @RequiredArgsConstructor
    public static class StepMockBuilder {
        private final MockRegistry registry;
        private final String workflowId;
        private final String stepId;
        
        /**
         * Creates a mock that always executes.
         * 
         * @return behavior builder
         */
        public BehaviorBuilder always() {
            return new BehaviorBuilder(registry, workflowId, stepId, null);
        }
        
        /**
         * Creates a conditional mock.
         * 
         * @param inputType the expected input type
         * @param condition the condition to evaluate
         * @param <I> input type
         * @return behavior builder
         */
        public <I> BehaviorBuilder when(Class<I> inputType, Predicate<I> condition) {
            Objects.requireNonNull(inputType, "inputType cannot be null");
            Objects.requireNonNull(condition, "condition cannot be null");
            return new BehaviorBuilder(registry, workflowId, stepId, new TypedCondition<>(inputType, condition));
        }
        
        /**
         * Creates a mock that fails a specific number of times before succeeding.
         * 
         * @param times number of times to fail
         * @return timed behavior builder
         */
        public TimedBehaviorBuilder times(int times) {
            if (times <= 0) {
                throw new IllegalArgumentException("times must be positive");
            }
            return new TimedBehaviorBuilder(registry, workflowId, stepId, times);
        }
    }
    
    /**
     * Builder for mock behavior.
     */
    @RequiredArgsConstructor
    public static class BehaviorBuilder {
        private final MockRegistry registry;
        private final String workflowId;
        private final String stepId;
        private final TypedCondition<?> condition;
        
        /**
         * Mock returns a specific result.
         * 
         * @param inputType the expected input type
         * @param resultProvider function to create the result
         * @param <I> input type
         * @param <O> output type
         */
        public <I, O> void thenReturn(Class<I> inputType, Function<I, StepResult<O>> resultProvider) {
            Objects.requireNonNull(inputType, "inputType cannot be null");
            Objects.requireNonNull(resultProvider, "resultProvider cannot be null");
            
            MockDefinition<I> mock = MockDefinition.of(workflowId, stepId, inputType, resultProvider);
            
            if (condition != null) {
                registry.registerConditional(workflowId, stepId, condition.castPredicate(), mock);
            } else {
                registry.register(mock);
            }
        }
        
        /**
         * Mock returns a fixed result.
         * 
         * @param inputType the expected input type
         * @param result the result to return
         * @param <I> input type
         * @param <O> output type
         */
        public <I, O> void thenReturn(Class<I> inputType, StepResult<O> result) {
            Objects.requireNonNull(result, "result cannot be null");
            thenReturn(inputType, input -> result);
        }
        
        /**
         * Mock throws an exception.
         * 
         * @param exception the exception to throw
         */
        public void thenFail(Exception exception) {
            Objects.requireNonNull(exception, "exception cannot be null");
            
            MockDefinition<Object> mock = MockDefinition.throwing(workflowId, stepId, Object.class, exception);
            
            if (condition != null) {
                registry.registerConditional(workflowId, stepId, condition.castPredicate(), mock);
            } else {
                registry.register(mock);
            }
        }
        
        /**
         * Mock succeeds with a simple value.
         * 
         * @param value the value to return
         * @param <O> output type
         */
        public <O> void thenSucceed(O value) {
            MockDefinition<Object> mock = MockDefinition.returning(
                workflowId, stepId, Object.class, StepResult.continueWith(value)
            );
            
            if (condition != null) {
                registry.registerConditional(workflowId, stepId, condition.castPredicate(), mock);
            } else {
                registry.register(mock);
            }
        }
    }
    
    /**
     * Builder for timed mock behavior.
     */
    @RequiredArgsConstructor
    public static class TimedBehaviorBuilder {
        private final MockRegistry registry;
        private final String workflowId;
        private final String stepId;
        private final int times;
        
        private Exception failureException = new RuntimeException("Mock failure");
        
        /**
         * Sets the exception to throw during failures.
         * 
         * @param exception the exception to throw
         * @return this builder
         */
        public TimedBehaviorBuilder thenFail(Exception exception) {
            Objects.requireNonNull(exception, "exception cannot be null");
            this.failureException = exception;
            return this;
        }
        
        /**
         * Sets what happens after the failures.
         * 
         * @return behavior builder for success case
         */
        public AfterFailureBuilder afterwards() {
            return new AfterFailureBuilder(registry, workflowId, stepId, times, failureException);
        }
    }
    
    /**
     * Builder for behavior after timed failures.
     */
    @RequiredArgsConstructor
    public static class AfterFailureBuilder {
        private final MockRegistry registry;
        private final String workflowId;
        private final String stepId;
        private final int failTimes;
        private final Exception failureException;
        
        /**
         * Mock succeeds after failures.
         * 
         * @param value the value to return on success
         * @param <O> output type
         */
        public <O> void thenSucceed(O value) {
            FailureThenSuccessMock mock = new FailureThenSuccessMock(
                workflowId, stepId, failTimes, failureException, StepResult.continueWith(value)
            );
            registry.register(mock);
        }
        
        /**
         * Mock returns specific result after failures.
         * 
         * @param inputType the expected input type
         * @param resultProvider function to create the result
         * @param <I> input type
         * @param <O> output type
         */
        public <I, O> void thenReturn(Class<I> inputType, Function<I, StepResult<O>> resultProvider) {
            Objects.requireNonNull(inputType, "inputType cannot be null");
            Objects.requireNonNull(resultProvider, "resultProvider cannot be null");
            
            // Cast to wildcard type
            @SuppressWarnings("unchecked")
            Function<I, StepResult<?>> wildcardProvider = (Function<I, StepResult<?>>) (Function<?, ?>) resultProvider;
            
            FailureThenSuccessMock mock = new FailureThenSuccessMock(
                workflowId, stepId, failTimes, failureException, 
                new DynamicResultProvider<I>(inputType, wildcardProvider)
            );
            registry.register(mock);
        }
    }
    
    /**
     * Container for typed condition.
     */
    private static class TypedCondition<I> {
        private final Class<I> inputType;
        private final Predicate<I> predicate;
        
        TypedCondition(Class<I> inputType, Predicate<I> predicate) {
            this.inputType = inputType;
            this.predicate = predicate;
        }
        
        @SuppressWarnings("unchecked")
        Predicate<Object> castPredicate() {
            return obj -> inputType.isInstance(obj) && predicate.test((I) obj);
        }
    }
    
    /**
     * Mock that fails a certain number of times then succeeds.
     */
    private static class FailureThenSuccessMock extends MockDefinition<Object> {
        private int attemptCount = 0;
        private final int failTimes;
        private final Exception failureException;
        private final Object successResult;
        
        FailureThenSuccessMock(String workflowId, String stepId, int failTimes, 
                              Exception failureException, Object successResult) {
            super(workflowId, stepId, Object.class, null);
            this.failTimes = failTimes;
            this.failureException = failureException;
            this.successResult = successResult;
        }
        
        @Override
        public StepResult<?> execute(Object input, StepContext context) {
            attemptCount++;
            if (attemptCount <= failTimes) {
                throw new RuntimeException(failureException);
            }
            
            if (successResult instanceof StepResult) {
                return (StepResult<?>) successResult;
            } else if (successResult instanceof DynamicResultProvider) {
                return ((DynamicResultProvider<?>) successResult).provide(input);
            } else {
                return StepResult.continueWith(successResult);
            }
        }
    }
    
    /**
     * Provider for dynamic results based on input.
     */
    private static class DynamicResultProvider<I> {
        private final Class<I> inputType;
        private final Function<I, StepResult<?>> provider;
        
        DynamicResultProvider(Class<I> inputType, Function<I, StepResult<?>> provider) {
            this.inputType = inputType;
            this.provider = provider;
        }
        
        @SuppressWarnings("unchecked")
        StepResult<?> provide(Object input) {
            if (!inputType.isInstance(input)) {
                throw new IllegalArgumentException(
                    "Expected input of type " + inputType + " but got " + 
                    (input != null ? input.getClass() : "null")
                );
            }
            return provider.apply((I) input);
        }
    }
}