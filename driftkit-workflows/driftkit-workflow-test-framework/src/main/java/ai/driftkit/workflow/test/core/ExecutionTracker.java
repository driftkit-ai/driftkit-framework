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
     * 
     * @param workflowId the workflow ID
     * @param stepId the step ID
     * @return execution count
     */
    public int getExecutionCount(String workflowId, String stepId) {
        Objects.requireNonNull(workflowId, "workflowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        
        String key = workflowId + "." + stepId;
        return executionCounts.getOrDefault(key, 0);
    }
    
    /**
     * Checks if a step was executed.
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
     * Immutable snapshot of execution history.
     */
    @Getter
    @RequiredArgsConstructor
    public static class ExecutionHistory {
        private final List<ExecutionRecord> executions;
        private final Map<String, Integer> executionCounts;
        
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