package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Test execution interceptor that allows mocking step behaviors and tracking executions.
 * This is the core mechanism for testing production workflows without modifying them.
 */
@Slf4j
@Getter
public class TestExecutionInterceptor implements ExecutionInterceptor {
    
    // Mocks for specific steps
    private final Map<String, Function<Object, StepResult<?>>> stepMocks = new ConcurrentHashMap<>();
    
    // Execution history for assertions
    private final Queue<ExecutionRecord> executionHistory = new ConcurrentLinkedQueue<>();
    
    // Step conditions for conditional mocking
    private final Map<String, List<ConditionalMock>> conditionalMocks = new ConcurrentHashMap<>();
    
    // Failure injections
    private final Map<String, FailureInjection> failureInjections = new ConcurrentHashMap<>();
    
    // Track current executing step for mocking
    private final ThreadLocal<StepContext> currentStepContext = new ThreadLocal<>();
    
    /**
     * Add a mock for a specific step in a workflow.
     * 
     * @param workflowId The workflow ID
     * @param stepId The step ID to mock
     * @param mockBehavior The mock behavior function
     */
    public <T> void addStepMock(String workflowId, String stepId, Function<T, StepResult<?>> mockBehavior) {
        String key = createKey(workflowId, stepId);
        @SuppressWarnings("unchecked")
        Function<Object, StepResult<?>> castMock = (Function<Object, StepResult<?>>) mockBehavior;
        stepMocks.put(key, castMock);
        log.debug("Added mock for {}.{}", workflowId, stepId);
    }
    
    /**
     * Add a conditional mock that only applies when a condition is met.
     */
    public <T> void addConditionalMock(String workflowId, String stepId, 
                                       Predicate<T> condition, 
                                       Function<T, StepResult<?>> mockBehavior) {
        String key = createKey(workflowId, stepId);
        @SuppressWarnings("unchecked")
        Predicate<Object> objectCondition = (Predicate<Object>) condition;
        @SuppressWarnings("unchecked")
        Function<Object, StepResult<?>> objectMockBehavior = (Function<Object, StepResult<?>>) mockBehavior;
        conditionalMocks.computeIfAbsent(key, k -> new ArrayList<>())
            .add(new ConditionalMock(objectCondition, objectMockBehavior));
    }
    
    /**
     * Inject a failure for a specific step.
     */
    public void injectFailure(String workflowId, String stepId, 
                             int afterAttempts, Exception exception) {
        String key = createKey(workflowId, stepId);
        failureInjections.put(key, new FailureInjection(afterAttempts, exception));
    }
    
    /**
     * Clear all mocks and history.
     */
    public void reset() {
        stepMocks.clear();
        executionHistory.clear();
        conditionalMocks.clear();
        failureInjections.clear();
    }
    
    /**
     * Get execution history for a specific step.
     */
    public List<ExecutionRecord> getStepHistory(String workflowId, String stepId) {
        String key = createKey(workflowId, stepId);
        return executionHistory.stream()
            .filter(record -> key.equals(createKey(record.workflowId, record.stepId)))
            .toList();
    }
    
    /**
     * Get all execution history.
     */
    public List<ExecutionRecord> getExecutionHistory() {
        return new ArrayList<>(executionHistory);
    }
    
    /**
     * Count executions of a specific step.
     */
    public int getExecutionCount(String workflowId, String stepId) {
        return getStepHistory(workflowId, stepId).size();
    }
    
    @Override
    public void beforeStep(WorkflowInstance instance, StepNode step, Object input) {
        String workflowId = instance.getWorkflowId();
        String stepId = step.id();
        
        // Set current context for potential mocking
        currentStepContext.set(new StepContext(workflowId, stepId, instance, step, input));
        
        // Record execution start
        ExecutionRecord record = new ExecutionRecord(
            workflowId,
            stepId,
            instance.getInstanceId(),
            input,
            null,
            Instant.now(),
            true
        );
        executionHistory.add(record);
        
        log.debug("Before step: {}.{}", workflowId, stepId);
    }
    
    @Override
    public void afterStep(WorkflowInstance instance, StepNode step, StepResult<?> result) {
        String workflowId = instance.getWorkflowId();
        String stepId = step.id();
        
        // Record execution completion
        ExecutionRecord record = new ExecutionRecord(
            workflowId,
            stepId,
            instance.getInstanceId(),
            currentStepContext.get() != null ? currentStepContext.get().input : null,
            result,
            Instant.now(),
            false
        );
        executionHistory.add(record);
        
        // Clear context
        currentStepContext.remove();
        
        log.debug("After step: {}.{} with result: {}", workflowId, stepId, result.getClass().getSimpleName());
    }
    
    @Override
    public void onStepError(WorkflowInstance instance, StepNode step, Exception error) {
        String workflowId = instance.getWorkflowId();
        String stepId = step.id();
        
        // Record error
        ExecutionRecord record = new ExecutionRecord(
            workflowId,
            stepId,
            instance.getInstanceId(),
            currentStepContext.get() != null ? currentStepContext.get().input : null,
            StepResult.fail(error.getMessage()),
            Instant.now(),
            false
        );
        executionHistory.add(record);
        
        // Clear context
        currentStepContext.remove();
        
        log.debug("Step error: {}.{} - {}", workflowId, stepId, error.getMessage());
    }
    
    @Override
    public Optional<StepResult<?>> interceptExecution(WorkflowInstance instance, StepNode step, Object input) {
        // Set the current context so getMockResult can use it
        currentStepContext.set(new StepContext(instance.getWorkflowId(), step.id(), instance, step, input));
        
        // Use the existing getMockResult logic
        Optional<StepResult<?>> mockResult = getMockResult();
        
        // If no mock is found, clear the context (it will be set again in beforeStep)
        if (mockResult.isEmpty()) {
            currentStepContext.remove();
        }
        
        return mockResult;
    }
    
    /**
     * Get mock result for current step if available.
     * This method should be called by a custom StepExecutor to apply mocks.
     */
    public Optional<StepResult<?>> getMockResult() {
        StepContext context = currentStepContext.get();
        if (context == null) {
            return Optional.empty();
        }
        
        String key = createKey(context.workflowId, context.stepId);
        
        // Check for failure injection
        FailureInjection failure = failureInjections.get(key);
        if (failure != null) {
            int currentAttempt = getExecutionCount(context.workflowId, context.stepId);
            if (currentAttempt <= failure.afterAttempts) {
                log.debug("Injecting failure for {}.{} on attempt {}", 
                    context.workflowId, context.stepId, currentAttempt);
                throw new RuntimeException(failure.exception);
            }
        }
        
        // Check for conditional mocks
        List<ConditionalMock> conditionals = conditionalMocks.get(key);
        if (conditionals != null) {
            for (ConditionalMock conditional : conditionals) {
                if (conditional.matches(context.input)) {
                    log.debug("Applying conditional mock for {}.{}", 
                        context.workflowId, context.stepId);
                    return Optional.of(conditional.apply(context.input));
                }
            }
        }
        
        // Check for step mock
        Function<Object, StepResult<?>> mock = stepMocks.get(key);
        if (mock != null) {
            log.debug("Applying mock for {}.{}", context.workflowId, context.stepId);
            return Optional.of(mock.apply(context.input));
        }
        
        return Optional.empty();
    }
    
    private String createKey(String workflowId, String stepId) {
        return workflowId + "." + stepId;
    }
    
    /**
     * Record of a step execution for history tracking.
     */
    public record ExecutionRecord(
        String workflowId,
        String stepId,
        String sessionId,
        Object input,
        StepResult<?> result,
        Instant timestamp,
        boolean isStart
    ) {}
    
    /**
     * Conditional mock that only applies when a condition is met.
     */
    private record ConditionalMock(
        Predicate<Object> condition,
        Function<Object, StepResult<?>> mockBehavior
    ) {
        @SuppressWarnings("unchecked")
        boolean matches(Object input) {
            try {
                return condition.test(input);
            } catch (ClassCastException e) {
                return false;
            }
        }
        
        StepResult<?> apply(Object input) {
            return mockBehavior.apply(input);
        }
    }
    
    /**
     * Failure injection configuration.
     */
    private record FailureInjection(
        int afterAttempts,
        Exception exception
    ) {}
    
    /**
     * Context for the currently executing step.
     */
    private record StepContext(
        String workflowId,
        String stepId,
        WorkflowInstance instance,
        StepNode step,
        Object input
    ) {}
}