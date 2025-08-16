package ai.driftkit.workflow.engine.core;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks step execution counts within workflow instances.
 * Used to enforce invocation limits and provide execution statistics.
 */
@Slf4j
public class StepExecutionTracker {
    
    // Map from workflow instance ID to step execution counts
    private final ConcurrentMap<String, ConcurrentMap<String, AtomicInteger>> executionCounts = new ConcurrentHashMap<>();
    
    /**
     * Records a step execution and returns the new count.
     * 
     * @param workflowInstanceId The workflow instance ID
     * @param stepId The step ID
     * @return The new execution count for this step
     */
    public int recordExecution(String workflowInstanceId, String stepId) {
        ConcurrentMap<String, AtomicInteger> stepCounts = executionCounts.computeIfAbsent(
            workflowInstanceId, k -> new ConcurrentHashMap<>()
        );
        
        AtomicInteger count = stepCounts.computeIfAbsent(stepId, k -> new AtomicInteger(0));
        int newCount = count.incrementAndGet();
        
        log.debug("Step {} in workflow {} executed {} times", stepId, workflowInstanceId, newCount);
        return newCount;
    }
    
    /**
     * Gets the current execution count for a step.
     * 
     * @param workflowInstanceId The workflow instance ID
     * @param stepId The step ID
     * @return The current execution count, or 0 if never executed
     */
    public int getExecutionCount(String workflowInstanceId, String stepId) {
        ConcurrentMap<String, AtomicInteger> stepCounts = executionCounts.get(workflowInstanceId);
        if (stepCounts == null) {
            return 0;
        }
        
        AtomicInteger count = stepCounts.get(stepId);
        return count != null ? count.get() : 0;
    }
    
    /**
     * Resets the execution count for a specific step.
     * 
     * @param workflowInstanceId The workflow instance ID
     * @param stepId The step ID
     */
    public void resetStepCount(String workflowInstanceId, String stepId) {
        ConcurrentMap<String, AtomicInteger> stepCounts = executionCounts.get(workflowInstanceId);
        if (stepCounts != null) {
            stepCounts.remove(stepId);
            log.debug("Reset execution count for step {} in workflow {}", stepId, workflowInstanceId);
        }
    }
    
    /**
     * Clears all execution counts for a workflow instance.
     * Should be called when a workflow completes or is cleaned up.
     * 
     * @param workflowInstanceId The workflow instance ID
     */
    public void clearWorkflowCounts(String workflowInstanceId) {
        ConcurrentMap<String, AtomicInteger> removed = executionCounts.remove(workflowInstanceId);
        if (removed != null) {
            log.debug("Cleared execution counts for workflow {}, had {} steps tracked", 
                workflowInstanceId, removed.size());
        }
    }
    
    /**
     * Gets all step execution counts for a workflow instance.
     * 
     * @param workflowInstanceId The workflow instance ID
     * @return Map of step IDs to execution counts
     */
    public ConcurrentMap<String, Integer> getWorkflowCounts(String workflowInstanceId) {
        ConcurrentMap<String, AtomicInteger> stepCounts = executionCounts.get(workflowInstanceId);
        if (stepCounts == null) {
            return new ConcurrentHashMap<>();
        }
        
        ConcurrentMap<String, Integer> result = new ConcurrentHashMap<>();
        stepCounts.forEach((stepId, count) -> result.put(stepId, count.get()));
        return result;
    }
    
    /**
     * Gets the total number of workflows being tracked.
     * 
     * @return The number of workflow instances with execution data
     */
    public int getTrackedWorkflowCount() {
        return executionCounts.size();
    }
    
    /**
     * Clears all execution tracking data.
     * Use with caution - this affects all workflows.
     */
    public void clearAll() {
        int workflowCount = executionCounts.size();
        executionCounts.clear();
        log.info("Cleared all execution tracking data for {} workflows", workflowCount);
    }
}