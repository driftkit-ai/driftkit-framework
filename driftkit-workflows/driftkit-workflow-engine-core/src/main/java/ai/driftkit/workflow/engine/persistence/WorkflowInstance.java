package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.domain.AsyncStepState;
import ai.driftkit.workflow.engine.domain.SuspensionData;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable representation of a running workflow instance.
 * This class maintains the current state of execution, including context,
 * status, and execution history.
 * 
 * <p>While WorkflowGraph is the immutable blueprint, WorkflowInstance
 * represents the runtime state that can be persisted and resumed.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstance {
    
    /**
     * Unique identifier for this workflow instance (same as runId in context).
     */
    private String instanceId;
    
    /**
     * Reference to the workflow definition.
     */
    private String workflowId;
    
    /**
     * Version of the workflow being executed.
     */
    private String workflowVersion;
    
    /**
     * Current execution context containing step outputs and state.
     */
    private WorkflowContext context;
    
    /**
     * Current status of the workflow execution.
     */
    private WorkflowStatus status;
    
    /**
     * ID of the current step being executed or waiting.
     */
    private String currentStepId;
    
    /**
     * ID of the next step to be executed (if determined).
     */
    private String nextStepId;
    
    /**
     * When the workflow instance was created.
     */
    private Instant createdAt;
    
    /**
     * When the workflow instance was last updated.
     */
    private Instant updatedAt;
    
    /**
     * When the workflow instance was completed (if applicable).
     */
    private Instant completedAt;
    
    /**
     * Execution history for debugging and tracing.
     */
    @Builder.Default
    private List<StepExecutionRecord> executionHistory = new ArrayList<>();
    
    /**
     * Metadata associated with this instance.
     */
    @Builder.Default
    private Map<String, Object> metadata = new ConcurrentHashMap<>();
    
    /**
     * Error information if the workflow failed.
     */
    private ErrorInfo errorInfo;
    
    /**
     * Suspension information if the workflow is suspended.
     */
    private SuspensionData suspensionData;
    
    /**
     * Async step states for tracking progress of asynchronous operations.
     */
    @Builder.Default
    private Map<String, AsyncStepState> asyncStepStates = new ConcurrentHashMap<>();
    
    /**
     * Creates a new workflow instance for a fresh run.
     */
    public static WorkflowInstance newInstance(WorkflowGraph<?, ?> graph, Object triggerData) {
        WorkflowContext context = WorkflowContext.newRun(triggerData, graph.workflowInstance());
        Instant now = Instant.now();
        
        return WorkflowInstance.builder()
            .instanceId(context.getRunId())
            .workflowId(graph.id())
            .workflowVersion(graph.version())
            .context(context)
            .status(WorkflowStatus.RUNNING)
            .currentStepId(graph.initialStepId())
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
    
    /**
     * Records the execution of a step.
     */
    public void recordStepExecution(String stepId, Object input, Object output, 
                                   long durationMs, boolean success) {
        StepExecutionRecord record = StepExecutionRecord.builder()
            .stepId(stepId)
            .input(input)
            .output(output)
            .executedAt(Instant.now())
            .durationMs(durationMs)
            .success(success)
            .build();
        
        executionHistory.add(record);
        updatedAt = Instant.now();
    }
    
    /**
     * Updates the workflow status.
     */
    public void updateStatus(WorkflowStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
        
        if (newStatus == WorkflowStatus.COMPLETED || newStatus == WorkflowStatus.FAILED) {
            this.completedAt = Instant.now();
        }
    }
    
    /**
     * Suspends the workflow with the given information.
     */
    public void suspend(SuspensionData suspensionData) {
        this.status = WorkflowStatus.SUSPENDED;
        this.suspensionData = suspensionData;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Resumes the workflow from suspension.
     */
    public void resume() {
        if (status != WorkflowStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot resume workflow that is not suspended");
        }
        
        this.status = WorkflowStatus.RUNNING;
        this.suspensionData = null;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Marks the workflow as failed with error information.
     */
    public void fail(Throwable error, String stepId) {
        this.status = WorkflowStatus.FAILED;
        this.errorInfo = new ErrorInfo(
            error.getClass().getName(),
            error.getMessage(),
            stepId,
            Instant.now(),
            getStackTrace(error)
        );
        this.updatedAt = Instant.now();
        this.completedAt = Instant.now();
    }
    
    /**
     * Updates the context with a new step output.
     */
    public void updateContext(String stepId, Object output) {
        this.context.setStepOutput(stepId, output);
        this.updatedAt = Instant.now();
    }
    
    /**
     * Sets a custom value in the workflow context.
     */
    public void setContextValue(String key, Object value) {
        this.context.setContextValue(key, value);
        this.updatedAt = Instant.now();
    }
    
    /**
     * Sets the async state for a step.
     */
    public void setAsyncStepState(String stepId, AsyncStepState state) {
        if (stepId == null || state == null) {
            throw new IllegalArgumentException("Step ID and state cannot be null");
        }
        asyncStepStates.put(stepId, state);
        this.updatedAt = Instant.now();
    }
    
    /**
     * Gets the async state for a specific step.
     */
    public Optional<AsyncStepState> getAsyncStepState(String stepId) {
        return Optional.ofNullable(asyncStepStates.get(stepId));
    }
    
    /**
     * Gets the async state for the current step.
     */
    public Optional<AsyncStepState> getCurrentAsyncState() {
        return getAsyncStepState(currentStepId);
    }
    
    /**
     * Removes async state for a step (used when step completes).
     */
    public void clearAsyncStepState(String stepId) {
        asyncStepStates.remove(stepId);
        this.updatedAt = Instant.now();
    }
    
    /**
     * Gets the total execution duration.
     */
    public long getTotalDurationMs() {
        if (createdAt == null) {
            return 0;
        }
        
        Instant endTime = completedAt != null ? completedAt : Instant.now();
        return endTime.toEpochMilli() - createdAt.toEpochMilli();
    }
    
    /**
     * Checks if the workflow is in a terminal state.
     */
    public boolean isTerminal() {
        return status == WorkflowStatus.COMPLETED || 
               status == WorkflowStatus.FAILED || 
               status == WorkflowStatus.CANCELLED;
    }
    
    /**
     * Extracts stack trace from exception.
     */
    private static String getStackTrace(Throwable error) {
        if (error == null) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(error).append("\n");
        
        StackTraceElement[] stackTrace = error.getStackTrace();
        int limit = Math.min(stackTrace.length, 10); // Limit stack trace depth
        
        for (int i = 0; i < limit; i++) {
            sb.append("\tat ").append(stackTrace[i]).append("\n");
        }
        
        if (stackTrace.length > limit) {
            sb.append("\t... ").append(stackTrace.length - limit).append(" more\n");
        }
        
        if (error.getCause() != null) {
            sb.append("Caused by: ").append(getStackTrace(error.getCause()));
        }
        
        return sb.toString();
    }
    
    /**
     * Workflow execution status.
     */
    public enum WorkflowStatus {
        /**
         * Workflow is actively executing.
         */
        RUNNING,
        
        /**
         * Workflow is suspended waiting for external input.
         */
        SUSPENDED,
        
        /**
         * Workflow completed successfully.
         */
        COMPLETED,
        
        /**
         * Workflow failed with an error.
         */
        FAILED,
        
        /**
         * Workflow was cancelled by user or system.
         */
        CANCELLED
    }
    
    /**
     * Record of a single step execution.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepExecutionRecord {
        private String stepId;
        private Object input;
        private Object output;
        private Instant executedAt;
        private long durationMs;
        private boolean success;
        private String errorMessage;
    }
    
    
    /**
     * Information about workflow error.
     */
    public record ErrorInfo(
        String errorType,
        String errorMessage,
        String stepId,
        Instant occurredAt,
        String stackTrace
    ) {}
}