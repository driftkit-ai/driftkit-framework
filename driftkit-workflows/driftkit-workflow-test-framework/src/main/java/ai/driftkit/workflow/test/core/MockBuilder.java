package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.StepResult;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
        
        /**
         * Creates a mock using an existing mock object (e.g., Mockito mock).
         * 
         * @param mockObject the mock object to use
         * @return registration builder
         */
        public MockRegistrationBuilder mockWith(Object mockObject) {
            Objects.requireNonNull(mockObject, "mockObject cannot be null");
            return new MockRegistrationBuilder(registry, workflowId, stepId, mockObject);
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
                // Use type-safe registration with the condition's input type and predicate
                condition.registerConditionalMock(registry, workflowId, stepId, mock);
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
                condition.registerConditionalMock(registry, workflowId, stepId, mock);
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
                condition.registerConditionalMock(registry, workflowId, stepId, mock);
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
            
            // Create a safe wrapper that converts the typed result to wildcard
            Function<I, StepResult<?>> wildcardProvider = input -> {
                StepResult<O> typedResult = resultProvider.apply(input);
                return typedResult; // Safe upcast from StepResult<O> to StepResult<?>
            };
            
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
        
        /**
         * Registers a conditional mock with proper type safety.
         */
        void registerConditionalMock(MockRegistry registry, String workflowId, String stepId, MockDefinition<?> mock) {
            registry.registerConditional(workflowId, stepId, inputType, predicate, mock);
        }
    }
    
    /**
     * Mock that fails a certain number of times then succeeds.
     */
    private static class FailureThenSuccessMock extends MockDefinition<Object> {
        // Use a map to track attempts per workflow instance to handle concurrent tests
        private final Map<String, Integer> attemptCounts = new ConcurrentHashMap<>();
        private final int failTimes;
        private final Exception failureException;
        private final Object successResult;
        // Track completed executions for cleanup
        private final Set<String> completedExecutions = ConcurrentHashMap.newKeySet();
        // Max entries before forced cleanup
        private static final int MAX_TRACKED_EXECUTIONS = 1000;
        
        FailureThenSuccessMock(String workflowId, String stepId, int failTimes, 
                              Exception failureException, Object successResult) {
            super(workflowId, stepId, Object.class, null);
            this.failTimes = failTimes;
            this.failureException = failureException;
            this.successResult = successResult;
        }
        
        @Override
        public StepResult<?> execute(Object input, StepContext context) {
            // Perform cleanup if too many entries
            if (attemptCounts.size() > MAX_TRACKED_EXECUTIONS) {
                cleanupCompletedExecutions();
            }
            
            // Use workflow instance ID to track attempts per execution
            String instanceKey = context.getRunId();
            int attemptCount = attemptCounts.compute(instanceKey, (k, v) -> v == null ? 1 : v + 1);
            
            if (attemptCount <= failTimes) {
                // Return StepResult.Fail instead of throwing exception
                // This ensures the retry mechanism sees the failure
                return StepResult.fail(failureException);
            }
            
            // Clean up the count after success to avoid memory leak
            attemptCounts.remove(instanceKey);
            completedExecutions.add(instanceKey);
            
            if (successResult instanceof StepResult) {
                return (StepResult<?>) successResult;
            } else if (successResult instanceof DynamicResultProvider) {
                return ((DynamicResultProvider<?>) successResult).provide(input);
            } else {
                return StepResult.continueWith(successResult);
            }
        }
        
        /**
         * Clean up completed executions from the attempt counts map.
         */
        private void cleanupCompletedExecutions() {
            completedExecutions.forEach(attemptCounts::remove);
            completedExecutions.clear();
        }
        
        /**
         * Clean up all tracked state.
         */
        public void cleanup() {
            attemptCounts.clear();
            completedExecutions.clear();
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
        
        /**
         * Provides result with safe type casting.
         * 
         * @param input the input object to process
         * @return the step result
         * @throws IllegalArgumentException if input type doesn't match expected type
         */
        StepResult<?> provide(Object input) {
            if (!inputType.isInstance(input)) {
                throw new IllegalArgumentException(
                    "Expected input of type " + inputType.getName() + " but got " + 
                    (input != null ? input.getClass().getName() : "null")
                );
            }
            
            try {
                I typedInput = inputType.cast(input);
                return provider.apply(typedInput);
            } catch (ClassCastException e) {
                // This should not happen since we checked with isInstance
                throw new IllegalStateException(
                    "Type casting failed: expected " + inputType.getName() + 
                    " but got " + (input != null ? input.getClass().getName() : "null"), e
                );
            }
        }
    }
    
    /**
     * Builder for registering existing mock objects.
     */
    @RequiredArgsConstructor
    public static class MockRegistrationBuilder {
        private final MockRegistry registry;
        private final String workflowId;
        private final String stepId;
        private final Object mockObject;
        
        /**
         * Registers the mock object.
         */
        public void register() {
            // Create a mock definition that delegates to the mock object
            MockDefinition<Object> mockDef = new DelegatingMockDefinition(
                workflowId, stepId, mockObject
            );
            registry.register(mockDef);
        }
    }
    
    /**
     * Mock definition that delegates to an external mock object.
     */
    private static class DelegatingMockDefinition extends MockDefinition<Object> {
        private final Object mockObject;
        
        DelegatingMockDefinition(String workflowId, String stepId, Object mockObject) {
            super(workflowId, stepId, Object.class, null);
            this.mockObject = mockObject;
        }
        
        @Override
        public StepResult<?> execute(Object input, StepContext context) {
            try {
                // Find and invoke method that matches the input type
                for (Method method : mockObject.getClass().getMethods()) {
                    if (method.getName().equals("process") || 
                        method.getName().equals("execute") ||
                        method.getName().equals("apply")) {
                        
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length == 1 && paramTypes[0].isInstance(input)) {
                            Object result = method.invoke(mockObject, input);
                            
                            // Convert result to StepResult if needed
                            if (result instanceof StepResult) {
                                return (StepResult<?>) result;
                            } else {
                                return StepResult.continueWith(result);
                            }
                        }
                    }
                }
                
                // Fallback: try to invoke with no args
                for (Method method : mockObject.getClass().getMethods()) {
                    if (method.getName().equals("process") || 
                        method.getName().equals("execute") ||
                        method.getName().equals("get")) {
                        
                        if (method.getParameterCount() == 0) {
                            Object result = method.invoke(mockObject);
                            
                            if (result instanceof StepResult) {
                                return (StepResult<?>) result;
                            } else {
                                return StepResult.continueWith(result);
                            }
                        }
                    }
                }
                
                throw new IllegalStateException(
                    "Mock object does not have a suitable method: " + mockObject.getClass()
                );
                
            } catch (Exception e) {
                return StepResult.fail(e);
            }
        }
    }
}