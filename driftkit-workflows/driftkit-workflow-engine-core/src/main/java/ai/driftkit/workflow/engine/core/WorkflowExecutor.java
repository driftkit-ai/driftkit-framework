package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

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
    
    public WorkflowExecutor(WorkflowEngineConfig config, ProgressTracker progressTracker) {
        this.config = config;
        this.progressTracker = progressTracker;
        this.inputPreparer = new InputPreparer();
        this.interceptors = new ArrayList<>();
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
        String stepId = step.id();
        log.debug("Executing step: {} (instance: {})", stepId, instance.getInstanceId());
        
        long startTime = System.currentTimeMillis();
        
        Object input = null;
        
        try {
            // Prepare input for the step
            input = inputPreparer.prepareStepInput(instance, step);
            
            // Call interceptors before execution
            notifyBeforeStep(instance, step, input);
            
            // Execute the step
            Object result = step.executor().execute(input, instance.getContext());
            
            // Validate result type
            if (!(result instanceof StepResult)) {
                throw new IllegalStateException(
                    "Step must return StepResult, got: " + 
                    (result != null ? result.getClass().getName() : "null")
                );
            }
            
            StepResult<?> stepResult = (StepResult<?>) result;
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
    
    
}