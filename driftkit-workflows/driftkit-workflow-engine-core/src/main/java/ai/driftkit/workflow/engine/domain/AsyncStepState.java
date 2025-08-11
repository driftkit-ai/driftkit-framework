package ai.driftkit.workflow.engine.domain;

import ai.driftkit.workflow.engine.core.StepResult;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured representation of an asynchronous step's execution state.
 * This class encapsulates all state related to an async operation,
 * eliminating the need for string-based keys in the context.
 */
@Data
@Builder
public class AsyncStepState {
    
    /**
     * The task ID - this is the @AsyncStep method ID (e.g., "executeTask").
     * NOT unique per execution - same for all concurrent executions of the same async step.
     */
    private final String taskId;
    
    /**
     * The unique message ID generated for external tracking.
     * This is unique per execution and used to track specific async operations.
     */
    private final String messageId;
    
    /**
     * The initial data object returned immediately to the user.
     */
    private final Object initialData;
    
    /**
     * The current data representing the async operation's state.
     * This is updated as the async operation progresses.
     */
    private Object currentData;
    
    /**
     * The percentage of completion (0-100).
     */
    private int percentComplete;
    
    /**
     * The current status message.
     */
    private String statusMessage;
    
    /**
     * When the async operation started.
     */
    private final Instant startTime;
    
    /**
     * When the async operation completed (if applicable).
     */
    private Instant completionTime;
    
    /**
     * The result data once the async operation completes.
     */
    private Object resultData;
    
    /**
     * The final StepResult once the async operation completes.
     */
    private StepResult<?> finalResult;
    
    /**
     * Error information if the async operation failed.
     */
    private Throwable error;
    
    /**
     * The status of the async operation.
     */
    private AsyncStatus status;
    
    public enum AsyncStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * Creates a new AsyncStepState for a starting async operation.
     * Generates a unique messageId for tracking this specific execution.
     */
    public static AsyncStepState started(String taskId, Object initialData) {
        // Generate unique message ID for this execution
        String messageId = UUID.randomUUID().toString();
        
        return AsyncStepState.builder()
            .taskId(taskId)
            .messageId(messageId)
            .initialData(initialData)
            .currentData(initialData)
            .percentComplete(0)
            .statusMessage("Started")
            .startTime(Instant.now())
            .status(AsyncStatus.IN_PROGRESS)
            .build();
    }
    
    /**
     * Updates the progress of this async operation.
     */
    public void updateProgress(int percentComplete, Object progressData) {
        this.percentComplete = percentComplete;
        this.currentData = progressData;
        
        // Extract status message if progressData is a string
        if (progressData instanceof String) {
            this.statusMessage = (String) progressData;
        }
    }
    
    /**
     * Marks this async operation as completed.
     */
    public void complete(Object resultData) {
        this.status = AsyncStatus.COMPLETED;
        this.percentComplete = 100;
        this.statusMessage = "Completed";
        this.resultData = resultData;
        this.currentData = resultData;
        this.completionTime = Instant.now();
    }
    
    /**
     * Marks this async operation as failed.
     */
    public void fail(Throwable error) {
        this.status = AsyncStatus.FAILED;
        this.error = error;
        this.completionTime = Instant.now();
        this.statusMessage = "Failed: " + error.getMessage();
    }
    
    /**
     * Marks this async operation as cancelled.
     */
    public void cancel() {
        this.status = AsyncStatus.CANCELLED;
        this.completionTime = Instant.now();
        this.statusMessage = "Cancelled";
    }
    
    /**
     * Gets the duration of this async operation in milliseconds.
     */
    public long getDurationMs() {
        Instant endTime = completionTime != null ? completionTime : Instant.now();
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
    
    /**
     * Checks if this async operation is still running.
     */
    public boolean isRunning() {
        return status == AsyncStatus.IN_PROGRESS;
    }
    
    /**
     * Checks if this async operation has completed (successfully or not).
     */
    public boolean isCompleted() {
        return status == AsyncStatus.COMPLETED || 
               status == AsyncStatus.FAILED || 
               status == AsyncStatus.CANCELLED;
    }
    
    /**
     * Gets the current data for conversion to ChatResponse.
     * Returns currentData if set, otherwise returns initialData.
     */
    public Object getCurrentData() {
        return currentData != null ? currentData : initialData;
    }
}