package ai.driftkit.workflow.test.core;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Tracks workflow and step executions for testing purposes.
 * Thread-safe implementation for concurrent test scenarios.
 */
@Slf4j
public class ExecutionTracker {
    
    private final List<ExecutionRecord> executions = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> executionCounts = new ConcurrentHashMap<>();
    
    /**
     * Records a workflow execution event.
     */
    public void recordWorkflowEvent(WorkflowInstance instance, ExecutionStatus status, Object data) {
        Objects.requireNonNull(instance, "instance cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        
        ExecutionRecord record = new ExecutionRecord(
            RecordType.WORKFLOW,
            status,
            instance.getWorkflowId(),
            instance.getInstanceId(),
            null, // no stepId for workflow
            data,
            System.currentTimeMillis()
        );
        
        executions.add(record);
        log.debug("Recorded workflow event: {}", record);
    }
    
    /**
     * Records a step execution event.
     */
    public void recordStepEvent(StepContext context, ExecutionStatus status, Object data) {
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        
        ExecutionRecord record = new ExecutionRecord(
            RecordType.STEP,
            status,
            context.getWorkflowId(),
            context.getRunId(),
            context.getStepId(),
            data,
            System.currentTimeMillis()
        );
        
        executions.add(record);
        
        if (status == ExecutionStatus.STARTED) {
            incrementExecutionCount(context.createKey());
        }
        
        log.debug("Recorded step event: {}", record);
    }
    
    // Convenience methods for WorkflowTestInterceptor
    public void recordWorkflowStart(WorkflowInstance instance, Object input) {
        recordWorkflowEvent(instance, ExecutionStatus.STARTED, input);
    }
    
    public void recordWorkflowComplete(WorkflowInstance instance, Object result) {
        recordWorkflowEvent(instance, ExecutionStatus.COMPLETED, result);
    }
    
    public void recordWorkflowError(WorkflowInstance instance, Throwable error) {
        recordWorkflowEvent(instance, ExecutionStatus.FAILED, error);
    }
    
    public void recordStepStart(StepContext context) {
        recordStepEvent(context, ExecutionStatus.STARTED, context.getInput());
    }
    
    public void recordStepComplete(StepContext context, StepResult<?> result) {
        recordStepEvent(context, ExecutionStatus.COMPLETED, result);
    }
    
    public void recordStepError(StepContext context, Throwable error) {
        recordStepEvent(context, ExecutionStatus.FAILED, error);
    }
    
    /**
     * Gets the execution history for analysis.
     * 
     * @return immutable execution history
     */
    public ExecutionHistory getHistory() {
        return new ExecutionHistory(
            List.copyOf(executions),
            Map.copyOf(executionCounts)
        );
    }
    
    /**
     * Gets the execution count for a specific step.
     * Supports both exact matches and partial matches for steps with prefixes.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @return execution count
     */
    public int getExecutionCount(String workflowId, String stepId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        
        // First try exact match
        String key = createKey(workflowId, stepId);
        Integer exactCount = executionCounts.get(key);
        if (exactCount != null) {
            return exactCount;
        }
        
        // Then try to find steps that contain the stepId (for branch-prefixed steps)
        String prefix = workflowId + ".";
        return executionCounts.entrySet().stream()
            .filter(entry -> 
                entry.getKey().startsWith(prefix) && 
                entry.getKey().contains(stepId)
            )
            .mapToInt(Map.Entry::getValue)
            .sum();
    }
    
    /**
     * Checks if a step was executed.
     * Supports both exact matches and partial matches for steps with prefixes.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @return true if the step was executed at least once
     */
    public boolean wasExecuted(String workflowId, String stepId) {
        return getExecutionCount(workflowId, stepId) > 0;
    }
    
    /**
     * Gets all step executions for a workflow.
     * 
     * @param workflowId the workflow ID
     * @return list of step execution records
     */
    public List<ExecutionRecord> getStepExecutions(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        
        return executions.stream()
            .filter(exec -> exec.getWorkflowId().equals(workflowId))
            .filter(exec -> exec.getType() == RecordType.STEP)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets all executed step IDs for a workflow.
     * 
     * @param workflowId the workflow ID
     * @return set of executed step IDs
     */
    public Set<String> getExecutedStepIds(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        
        return getStepExecutions(workflowId).stream()
            .filter(exec -> exec.getStatus() == ExecutionStatus.COMPLETED)
            .map(ExecutionRecord::getStepId)
            .collect(Collectors.toSet());
    }
    
    /**
     * Gets the list of executed step IDs for a workflow.
     * 
     * @param workflowId the workflow ID
     * @return list of step IDs that were executed
     */
    public List<String> getExecutedSteps(String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        
        return executions.stream()
            .filter(exec -> exec.getWorkflowId().equals(workflowId))
            .filter(exec -> exec.getType() == RecordType.STEP)
            .filter(exec -> exec.getStatus() == ExecutionStatus.STARTED)
            .map(ExecutionRecord::getStepId)
            .distinct()
            .collect(Collectors.toList());
    }
    
    /**
     * Increments the execution count for a specific step.
     * This is used to track retry attempts.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     */
    public void incrementStepExecution(String workflowId, String stepId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        
        String key = createKey(workflowId, stepId);
        incrementExecutionCount(key);
        
        log.debug("Incremented execution count for {}.{} to {}", 
            workflowId, stepId, executionCounts.get(key));
    }

    /**
     * Clears all tracked executions.
     */
    public void clear() {
        log.debug("Clearing execution tracker");
        executions.clear();
        executionCounts.clear();
    }
    
    private void incrementExecutionCount(String key) {
        executionCounts.merge(key, 1, Integer::sum);
    }
    
    /**
     * Creates a unique key for workflow and step combination.
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @return unique key in format "workflowId.stepId"
     */
    private String createKey(String workflowId, String stepId) {
        return workflowId + "." + stepId;
    }
    
    /**
     * Immutable snapshot of execution history.
     */
    @Getter
    @RequiredArgsConstructor
    public static class ExecutionHistory {
        private final List<ExecutionRecord> executions;
        private final Map<String, Integer> executionCounts;
        
        /**
         * Gets all execution records.
         * 
         * @return list of all records
         */
        public List<ExecutionRecord> getRecords() {
            return executions;
        }
        
        /**
         * Gets executions in chronological order.
         * 
         * @return ordered list of executions
         */
        public List<ExecutionRecord> getOrderedExecutions() {
            return executions.stream()
                .sorted(Comparator.comparing(ExecutionRecord::getTimestamp))
                .collect(Collectors.toList());
        }
        
        /**
         * Gets only step executions.
         * 
         * @return list of step executions
         */
        public List<ExecutionRecord> getStepExecutions() {
            return executions.stream()
                .filter(exec -> exec.getType() == RecordType.STEP)
                .collect(Collectors.toList());
        }
        
        /**
         * Gets only workflow executions.
         * 
         * @return list of workflow executions
         */
        public List<ExecutionRecord> getWorkflowExecutions() {
            return executions.stream()
                .filter(exec -> exec.getType() == RecordType.WORKFLOW)
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Universal execution record.
     */
    @Getter
    @RequiredArgsConstructor
    public static class ExecutionRecord {
        private final RecordType type;
        private final ExecutionStatus status;
        private final String workflowId;
        private final String runId;
        private final String stepId;
        private final Object data;
        private final long timestamp;
        
        @Override
        public String toString() {
            return String.format("ExecutionRecord{type=%s, status=%s, workflow=%s, step=%s, timestamp=%d}",
                type, status, workflowId, stepId, timestamp);
        }
    }
    
    /**
     * Type of record.
     */
    public enum RecordType {
        WORKFLOW,
        STEP
    }
    
    /**
     * Execution status.
     */
    public enum ExecutionStatus {
        STARTED,
        COMPLETED,
        FAILED
    }
}