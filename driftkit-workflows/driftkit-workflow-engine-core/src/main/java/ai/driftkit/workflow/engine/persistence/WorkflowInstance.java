package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.chat.ChatDomain;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private long createdAt;
    
    /**
     * When the workflow instance was last updated.
     */
    private long updatedAt;
    
    /**
     * When the workflow instance was completed (if applicable).
     */
    private long completedAt;
    
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
     * Creates a new workflow instance for a fresh run.
     */
    public static WorkflowInstance newInstance(WorkflowGraph<?, ?> graph, Object triggerData) {
        String instanceId = UUID.randomUUID().toString();
        return newInstance(graph, triggerData, instanceId);
    }
    
    /**
     * Creates a new workflow instance with specific instance ID.
     */
    public static WorkflowInstance newInstance(WorkflowGraph<?, ?> graph, Object triggerData, String instanceId) {
        WorkflowContext context = WorkflowContext.newRun(triggerData, instanceId);
        long now = System.currentTimeMillis();
        
        return WorkflowInstance.builder()
            .instanceId(instanceId)
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
            .executedAt(System.currentTimeMillis())
            .durationMs(durationMs)
            .success(success)
            .build();
        
        executionHistory.add(record);
        updatedAt = System.currentTimeMillis();
    }
    
    /**
     * Updates the workflow status.
     */
    public void updateStatus(WorkflowStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = System.currentTimeMillis();
        
        if (newStatus == WorkflowStatus.COMPLETED || newStatus == WorkflowStatus.FAILED) {
            this.completedAt = System.currentTimeMillis();
        }
    }
    
    /**
     * Suspends the workflow.
     * Note: SuspensionData is now stored separately in SuspensionDataRepository
     */
    public void suspend() {
        this.status = WorkflowStatus.SUSPENDED;
        this.updatedAt = System.currentTimeMillis();
    }
    
    /**
     * Resumes the workflow from suspension.
     */
    public void resume() {
        if (status != WorkflowStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot resume workflow that is not suspended");
        }
        
        this.status = WorkflowStatus.RUNNING;
        this.updatedAt = System.currentTimeMillis();
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
            System.currentTimeMillis(),
            getStackTrace(error)
        );
        this.updatedAt = System.currentTimeMillis();
        this.completedAt = System.currentTimeMillis();
    }
    
    /**
     * Updates the context with a new step output.
     */
    public void updateContext(String stepId, Object output) {
        this.context.setStepOutput(stepId, output);
        this.updatedAt = System.currentTimeMillis();
    }
    
    /**
     * Sets a custom value in the workflow context.
     */
    public void setContextValue(String key, Object value) {
        this.context.setContextValue(key, value);
        this.updatedAt = System.currentTimeMillis();
    }
    
    
    /**
     * Gets the total execution duration.
     */
    public long getTotalDurationMs() {
        if (createdAt == 0) {
            return 0;
        }
        
        long endTime = completedAt != 0 ? completedAt : System.currentTimeMillis();
        return endTime - createdAt;
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
        private long executedAt;
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
        long occurredAt,
        String stackTrace
    ) {}
}