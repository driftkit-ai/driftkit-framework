package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.ExecutionInterceptor;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

/**
 * Test interceptor that integrates with the test framework.
 * Provides mock execution and tracking capabilities.
 */
@Slf4j
public class WorkflowTestInterceptor implements ExecutionInterceptor {
    
    @Getter
    private final MockRegistry mockRegistry;
    
    @Getter
    private final ExecutionTracker executionTracker;
    
    private final ThreadLocal<StepContext> currentStepContext = new ThreadLocal<>();
    
    /**
     * Creates an interceptor with the provided mock registry and execution tracker.
     * This allows sharing of components between test context and interceptor.
     * 
     * @param mockRegistry the mock registry to use
     * @param executionTracker the execution tracker to use
     */
    public WorkflowTestInterceptor(MockRegistry mockRegistry, ExecutionTracker executionTracker) {
        this.mockRegistry = Objects.requireNonNull(mockRegistry, "mockRegistry cannot be null");
        this.executionTracker = Objects.requireNonNull(executionTracker, "executionTracker cannot be null");
    }
    
    /**
     * Creates an interceptor with the provided mock registry.
     * This allows sharing of mocks between test context and interceptor.
     * 
     * @param mockRegistry the mock registry to use
     */
    public WorkflowTestInterceptor(MockRegistry mockRegistry) {
        this(mockRegistry, new ExecutionTracker());
    }
    
    /**
     * Creates an interceptor with a new mock registry.
     * For backward compatibility.
     */
    public WorkflowTestInterceptor() {
        this(new MockRegistry(), new ExecutionTracker());
    }
    
    public void beforeWorkflowStart(WorkflowInstance instance, Object input) {
        log.debug("Before workflow start: {} with input: {}", instance.getWorkflowId(), input);
        executionTracker.recordWorkflowStart(instance, input);
    }
    
    public void afterWorkflowComplete(WorkflowInstance instance, Object result) {
        log.debug("After workflow complete: {} with result: {}", instance.getWorkflowId(), result);
        executionTracker.recordWorkflowComplete(instance, result);
    }
    
    public void onWorkflowError(WorkflowInstance instance, Throwable error) {
        log.debug("On workflow error: {} with error: {}", instance.getWorkflowId(), error.getMessage());
        executionTracker.recordWorkflowError(instance, error);
    }
    
    @Override
    public void beforeStep(WorkflowInstance instance, StepNode step, Object input) {
        log.debug("Before step execution: {}.{} with input: {}", 
            instance.getWorkflowId(), step.id(), input);
        
        StepContext context = new StepContext(instance, step, input);
        currentStepContext.set(context);
        executionTracker.recordStepStart(context);
    }
    
    @Override
    public void afterStep(WorkflowInstance instance, StepNode step, StepResult<?> result) {
        log.debug("After step execution: {}.{} with result: {}", 
            instance.getWorkflowId(), step.id(), result);
        
        try {
            StepContext context = currentStepContext.get();
            if (context != null) {
                executionTracker.recordStepComplete(context, result);
            }
        } finally {
            // Always remove ThreadLocal even if exception occurs
            currentStepContext.remove();
        }
    }
    
    @Override
    public void onStepError(WorkflowInstance instance, StepNode step, Exception error) {
        log.debug("On step error: {}.{} with error: {}", 
            instance.getWorkflowId(), step.id(), error.getMessage());
        
        try {
            StepContext context = currentStepContext.get();
            if (context != null) {
                executionTracker.recordStepError(context, error);
            }
        } finally {
            // Always remove ThreadLocal even if exception occurs
            currentStepContext.remove();
        }
    }
    
    @Override
    public Optional<StepResult<?>> interceptExecution(WorkflowInstance instance, StepNode step, Object input) {
        String workflowId = instance.getWorkflowId();
        String stepId = step.id();
        
        log.debug("Intercepting execution: {}.{} with input: {}", workflowId, stepId, input);
        
        // Create step context
        StepContext context = new StepContext(instance, step, input);
        
        // Try to find a mock
        Optional<MockDefinition<?>> mockOpt = mockRegistry.findMock(context);
        
        if (mockOpt.isPresent()) {
            MockDefinition<?> mock = mockOpt.get();
            log.debug("Found mock for {}.{}", workflowId, stepId);
            
            try {
                StepResult<?> result = mock.execute(input, context);
                log.debug("Mock execution successful for {}.{} returned: {}", workflowId, stepId, result);
                
                // If the mock returns StepResult.fail(), we should NOT return it as a result.
                // Instead, we should throw an exception so the RetryExecutor can handle retries properly.
                // The interceptor should only return successful results.
                if (result instanceof StepResult.Fail) {
                    StepResult.Fail<?> failResult = (StepResult.Fail<?>) result;
                    Throwable error = failResult.error();
                    log.debug("Mock returned StepResult.fail(), throwing exception: {}", error.getMessage());
                    if (error instanceof RuntimeException) {
                        throw (RuntimeException) error;
                    } else {
                        throw new RuntimeException("Mock returned fail result", error);
                    }
                }
                
                return Optional.of(result);
            } catch (RuntimeException e) {
                // For retry testing, we need to let the original exception through
                log.debug("Mock execution threw exception for {}.{}", workflowId, stepId, e);
                throw e;
            } catch (Exception e) {
                log.error("Mock execution failed for {}.{}", workflowId, stepId, e);
                throw new WorkflowTestException(
                    "Mock execution failed for " + workflowId + "." + stepId, e
                );
            }
        }
        
        log.debug("No mock found for {}.{}, using real implementation", workflowId, stepId);
        return Optional.empty();
    }
    
    /**
     * Clears all mocks and execution history.
     */
    public void clear() {
        log.debug("Clearing test interceptor state");
        mockRegistry.clear();
        executionTracker.clear();
        currentStepContext.remove();
    }
    
    /**
     * Gets the current step context (for internal use).
     * 
     * @return current step context or null
     */
    StepContext getCurrentStepContext() {
        return currentStepContext.get();
    }
}