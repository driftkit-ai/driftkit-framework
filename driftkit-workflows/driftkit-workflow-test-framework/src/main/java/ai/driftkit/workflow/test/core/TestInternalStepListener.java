package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.InternalStepListener;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Test implementation of InternalStepListener that tracks and mocks internal step executions.
 */
@Slf4j
@RequiredArgsConstructor
public class TestInternalStepListener implements InternalStepListener {
    
    private final WorkflowInstance workflowInstance;
    private final ExecutionTracker executionTracker;
    private final MockRegistry mockRegistry;
    
    @Override
    public void beforeInternalStep(String stepId, Object input, WorkflowContext context) {
        log.debug("Before internal step: {}.{}", workflowInstance.getWorkflowId(), stepId);
        
        if (executionTracker != null) {
            // Create virtual node for tracking
            StepNode virtualNode = StepNode.fromFunction(stepId, 
                obj -> StepResult.continueWith(obj), Object.class, Object.class);
            StepContext stepContext = new StepContext(workflowInstance, virtualNode, input);
            executionTracker.recordStepStart(stepContext);
        }
    }
    
    @Override
    public void afterInternalStep(String stepId, StepResult<?> result, WorkflowContext context) {
        log.debug("After internal step: {}.{} with result: {}", workflowInstance.getWorkflowId(), stepId, result);
        
        if (executionTracker != null) {
            StepNode virtualNode = StepNode.fromFunction(stepId, 
                obj -> StepResult.continueWith(obj), Object.class, Object.class);
            StepContext stepContext = new StepContext(workflowInstance, virtualNode, null);
            executionTracker.recordStepComplete(stepContext, result);
        }
    }
    
    @Override
    public void onInternalStepError(String stepId, Exception error, WorkflowContext context) {
        log.error("Internal step error: {}.{}", workflowInstance.getWorkflowId(), stepId, error);
        
        if (executionTracker != null) {
            StepNode virtualNode = StepNode.fromFunction(stepId, 
                obj -> StepResult.continueWith(obj), Object.class, Object.class);
            StepContext stepContext = new StepContext(workflowInstance, virtualNode, null);
            executionTracker.recordStepError(stepContext, error);
        }
    }
    
    @Override
    public Optional<StepResult<?>> interceptInternalStep(String stepId, Object input, WorkflowContext context) {
        if (mockRegistry != null) {
            // Create virtual node for mock lookup
            StepNode virtualNode = StepNode.fromFunction(stepId, 
                obj -> StepResult.continueWith(obj), Object.class, Object.class);
            StepContext stepContext = new StepContext(workflowInstance, virtualNode, input);
            
            // Check if there's a mock for this step
            var mockOpt = mockRegistry.findMock(stepContext);
            if (mockOpt.isPresent()) {
                log.debug("Found mock for internal step: {}.{}", workflowInstance.getWorkflowId(), stepId);
                try {
                    StepResult<?> result = mockOpt.get().execute(input, stepContext);
                    
                    // Return the mock result as-is, including StepResult.Fail
                    // The workflow builder's executeStepWithRetry will handle fail results properly
                    
                    return Optional.of(result);
                } catch (RuntimeException e) {
                    // For retry testing, we need to let the original exception through
                    log.debug("Mock execution threw exception for internal step {}.{}", 
                        workflowInstance.getWorkflowId(), stepId, e);
                    throw e;
                } catch (Exception e) {
                    log.error("Mock execution failed for internal step: {}.{}", 
                        workflowInstance.getWorkflowId(), stepId, e);
                    throw new RuntimeException("Mock execution failed", e);
                }
            }
        }
        
        return Optional.empty();
    }
}