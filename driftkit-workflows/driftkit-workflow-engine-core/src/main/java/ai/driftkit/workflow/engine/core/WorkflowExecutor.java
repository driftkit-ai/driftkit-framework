package ai.driftkit.workflow.engine.core;

import ai.driftkit.common.service.ChatStore;
import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles the execution of individual workflow steps.
 * This component is responsible for preparing step inputs, invoking step logic,
 * and recording execution results.
 */
@Slf4j
public class WorkflowExecutor {
    
    private final WorkflowEngineConfig config;
    private final ProgressTracker progressTracker;
    private final InputPreparer inputPreparer;
    private final List<ExecutionInterceptor> interceptors;
    private final RetryExecutor retryExecutor;

    public WorkflowExecutor(WorkflowEngineConfig config, ProgressTracker progressTracker, ChatStore chatStore) {
        this.config = config;
        this.progressTracker = progressTracker;
        this.inputPreparer = new InputPreparer();
        this.interceptors = new ArrayList<>();
        this.retryExecutor = config.getRetryExecutor() != null ? 
            config.getRetryExecutor() : new RetryExecutor();
        
        // Add chat tracking interceptor if ChatStore is available
        if (chatStore != null) {
            this.interceptors.add(new ChatTrackingInterceptor(chatStore));
        }
    }
    
    /**
     * Executes a single workflow step.
     * 
     * @param instance The workflow instance
     * @param step The step to execute
     * @param graph The workflow graph
     * @return The step result
     * @throws Exception if execution fails
     */
    public StepResult<?> executeStep(WorkflowInstance instance, 
                                    StepNode step,
                                    WorkflowGraph<?, ?> graph) throws Exception {
        // Delegate to retry executor
        return retryExecutor.executeWithRetry(instance, step, (inst, stp) -> {
            return executeStepInternal(inst, stp, graph);
        });
    }
    
    /**
     * Internal step execution logic without retry.
     */
    private StepResult<?> executeStepInternal(WorkflowInstance instance,
                                             StepNode step,
                                             WorkflowGraph<?, ?> graph) throws Exception {
        String stepId = step.id();
        log.debug("Executing step: {} (instance: {})", stepId, instance.getInstanceId());
        
        long startTime = System.currentTimeMillis();
        
        Object input = null;
        
        try {
            // Prepare input for the step
            input = inputPreparer.prepareStepInput(instance, step);
            
            log.debug("Step {} expects input type: {}, prepared input: {} (type: {})", 
                stepId, 
                step.executor().getInputType() != null ? step.executor().getInputType().getName() : "any",
                input,
                input != null ? input.getClass().getName() : "null");
            
            // Call interceptors before execution
            notifyBeforeStep(instance, step, input);
            
            // Check if any interceptor wants to override the execution
            Optional<StepResult<?>> interceptedResult = checkInterceptors(instance, step, input);
            
            StepResult<?> stepResult;
            if (interceptedResult.isPresent()) {
                // Use the intercepted result instead of executing the step
                stepResult = interceptedResult.get();
                log.debug("Step execution intercepted for: {} with result type: {}", 
                    stepId, stepResult.getClass().getSimpleName());
            } else {
                // Execute the step normally
                Object result = step.executor().execute(input, instance.getContext());
                
                // Auto-wrap non-StepResult values
                if (result instanceof StepResult) {
                    stepResult = (StepResult<?>) result;
                } else {
                    // Automatically wrap plain values in StepResult.continueWith()
                    log.debug("Auto-wrapping step result of type {} in StepResult.continueWith()", 
                        result != null ? result.getClass().getSimpleName() : "null");
                    stepResult = StepResult.continueWith(result);
                }
            }
            long duration = System.currentTimeMillis() - startTime;
            
            // Record execution
            instance.recordStepExecution(stepId, input, stepResult, duration, true);
            
            // Call interceptors after execution
            notifyAfterStep(instance, step, stepResult);
            
            log.debug("Step completed: {} in {}ms", stepId, duration);
            
            return stepResult;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            instance.recordStepExecution(stepId, input, null, duration, false);
            
            // Call interceptors on error
            notifyOnError(instance, step, e);
            
            throw e;
        }
    }
    
    /**
     * Adds an execution interceptor.
     */
    public void addInterceptor(ExecutionInterceptor interceptor) {
        if (interceptor != null) {
            interceptors.add(interceptor);
        }
    }
    
    /**
     * Gets the retry executor used by this workflow executor.
     * 
     * @return The retry executor
     */
    public RetryExecutor getRetryExecutor() {
        return retryExecutor;
    }
    
    /**
     * Removes an execution interceptor.
     */
    public void removeInterceptor(ExecutionInterceptor interceptor) {
        interceptors.remove(interceptor);
    }
    
    private void notifyBeforeStep(WorkflowInstance instance, StepNode step, Object input) {
        if (CollectionUtils.isEmpty(interceptors)) {
            return;
        }
        
        for (ExecutionInterceptor interceptor : interceptors) {
            try {
                interceptor.beforeStep(instance, step, input);
            } catch (Exception e) {
                log.warn("Interceptor {} failed in beforeStep", interceptor.getClass().getSimpleName(), e);
            }
        }
    }
    
    private void notifyAfterStep(WorkflowInstance instance, StepNode step, StepResult<?> result) {
        if (CollectionUtils.isEmpty(interceptors)) {
            return;
        }
        
        for (ExecutionInterceptor interceptor : interceptors) {
            try {
                interceptor.afterStep(instance, step, result);
            } catch (Exception e) {
                log.warn("Interceptor {} failed in afterStep", interceptor.getClass().getSimpleName(), e);
            }
        }
    }
    
    private void notifyOnError(WorkflowInstance instance, StepNode step, Exception error) {
        if (CollectionUtils.isEmpty(interceptors)) {
            return;
        }
        
        for (ExecutionInterceptor interceptor : interceptors) {
            try {
                interceptor.onStepError(instance, step, error);
            } catch (Exception e) {
                log.warn("Interceptor {} failed in onStepError", interceptor.getClass().getSimpleName(), e);
            }
        }
    }
    
    private Optional<StepResult<?>> checkInterceptors(WorkflowInstance instance, StepNode step, Object input) {
        if (CollectionUtils.isEmpty(interceptors)) {
            return Optional.empty();
        }
        
        for (ExecutionInterceptor interceptor : interceptors) {
            try {
                Optional<StepResult<?>> result = interceptor.interceptExecution(instance, step, input);
                if (result.isPresent()) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("Interceptor {} failed in interceptExecution", interceptor.getClass().getSimpleName(), e);
            }
        }
        
        return Optional.empty();
    }
    
}