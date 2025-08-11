package ai.driftkit.workflow.engine.async;

import ai.driftkit.workflow.engine.domain.WorkflowEvent;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Service interface for tracking asynchronous workflow execution and progress.
 * Ported from AsyncResponseTracker in driftkit-chat-assistant-framework
 */
public interface ProgressTracker {
    
    /**
     * Generate a unique response/task ID
     * 
     * @return A unique ID
     */
    String generateTaskId();
    
    /**
     * Track a workflow execution for asynchronous processing
     * 
     * @param taskId The task ID
     * @param event The workflow event to track
     */
    void trackExecution(String taskId, WorkflowEvent event);
    
    /**
     * Update the status of a tracked execution
     * 
     * @param taskId The task ID
     * @param event The updated event
     */
    void updateExecutionStatus(String taskId, WorkflowEvent event);
    
    /**
     * Update progress for a specific task
     * 
     * @param taskId The task ID
     * @param percentComplete Progress percentage (0-100)
     * @param message Progress message
     */
    void updateProgress(String taskId, int percentComplete, String message);
    
    /**
     * Get a tracked execution by ID
     * 
     * @param taskId The task ID
     * @return The workflow event if found
     */
    Optional<WorkflowEvent> getExecution(String taskId);
    
    /**
     * Remove a tracked execution
     * 
     * @param taskId The task ID to remove
     */
    void removeExecution(String taskId);
    
    /**
     * Execute a task asynchronously and track its progress
     * 
     * @param <T> The type of result
     * @param taskId The task ID for tracking
     * @param initialEvent The initial event to return immediately
     * @param task The async task to execute
     * @return A CompletableFuture that completes when the task is done
     */
    <T> CompletableFuture<T> executeAsync(
            String taskId, 
            WorkflowEvent initialEvent,
            Supplier<T> task);
    
    /**
     * Mark a task as completed
     * 
     * @param taskId The task ID
     * @param result The completion result
     */
    void onComplete(String taskId, Object result);
    
    /**
     * Mark a task as failed
     * 
     * @param taskId The task ID
     * @param error The error that occurred
     */
    void onError(String taskId, Throwable error);
    
    /**
     * Get the current progress for a task
     * 
     * @param taskId The task ID
     * @return Progress information
     */
    Optional<Progress> getProgress(String taskId);
    
    /**
     * Progress information for a task
     */
    record Progress(
        String taskId,
        int percentComplete,
        String message,
        ProgressStatus status,
        long startTime,
        Long endTime
    ) {
        public enum ProgressStatus {
            PENDING,
            IN_PROGRESS,
            COMPLETED,
            FAILED,
            CANCELLED
        }
        
        public static Progress started(String taskId) {
            return new Progress(taskId, 0, "Started", ProgressStatus.IN_PROGRESS, System.currentTimeMillis(), null);
        }
        
        public Progress withUpdate(int percentComplete, String message) {
            return new Progress(taskId, percentComplete, message, status, startTime, endTime);
        }
        
        public Progress completed() {
            return new Progress(taskId, 100, "Completed", ProgressStatus.COMPLETED, startTime, System.currentTimeMillis());
        }
        
        public Progress failed(String error) {
            return new Progress(taskId, percentComplete, error, ProgressStatus.FAILED, startTime, System.currentTimeMillis());
        }
    }
}