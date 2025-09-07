package ai.driftkit.workflow.engine.async;

import ai.driftkit.workflow.engine.async.ProgressTracker.Progress.ProgressStatus;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory implementation of ProgressTracker for development and testing.
 * For production, consider using Redis or database-backed implementation.
 */
@Slf4j
public class InMemoryProgressTracker implements ProgressTracker {
    
    private final Map<String, WorkflowEvent> executions = new ConcurrentHashMap<>();
    private final Map<String, Progress> progressMap = new ConcurrentHashMap<>();
    
    @Override
    public String generateTaskId() {
        return UUID.randomUUID().toString();
    }
    
    @Override
    public void trackExecution(String taskId, WorkflowEvent event) {
        executions.put(taskId, event);
        progressMap.put(taskId, Progress.started(taskId));
        log.debug("Tracking execution: taskId={}, event={}", taskId, event);
    }
    
    @Override
    public void updateExecutionStatus(String taskId, WorkflowEvent event) {
        executions.put(taskId, event);
        log.debug("Updated execution status: taskId={}, event={}", taskId, event);
    }
    
    @Override
    public void updateProgress(String taskId, int percentComplete, String message) {
        progressMap.compute(taskId, (id, existing) -> {
            if (existing == null) {
                return new Progress(
                        taskId,
                        percentComplete,
                        message,
                        percentComplete == 100 ? ProgressStatus.COMPLETED : ProgressStatus.IN_PROGRESS,
                        System.currentTimeMillis(),
                        null
                );
            }
            return existing.withUpdate(percentComplete, message);
        });
        
        // Update the workflow event as well
        WorkflowEvent event = executions.get(taskId);
        if (event != null) {
            event.updateProgress(percentComplete, message);
        }
        
        log.debug("Updated progress: taskId={}, percent={}, message={}", taskId, percentComplete, message);
    }
    
    @Override
    public Optional<WorkflowEvent> getExecution(String taskId) {
        return Optional.ofNullable(executions.get(taskId));
    }
    
    @Override
    public void removeExecution(String taskId) {
        executions.remove(taskId);
        progressMap.remove(taskId);
        log.debug("Removed execution: taskId={}", taskId);
    }
    
    @Override
    public <T> CompletableFuture<T> executeAsync(
            String taskId, 
            WorkflowEvent initialEvent,
            Supplier<T> task) {
        
        trackExecution(taskId, initialEvent);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Starting async execution: taskId={}", taskId);
                T result = task.get();
                onComplete(taskId, result);
                return result;
            } catch (Exception e) {
                log.error("Async execution failed: taskId={}", taskId, e);
                onError(taskId, e);
                throw new RuntimeException("Async execution failed", e);
            }
        });
    }
    
    @Override
    public void onComplete(String taskId, Object result) {
        progressMap.compute(taskId, (id, existing) -> {
            if (existing == null) {
                return new Progress(taskId, 100, "Completed", 
                    Progress.ProgressStatus.COMPLETED, System.currentTimeMillis(), System.currentTimeMillis());
            }
            return existing.completed();
        });
        
        // Update workflow event
        WorkflowEvent event = executions.get(taskId);
        if (event != null) {
            event.setCompleted(true);
            event.setPercentComplete(100);
            if (result != null) {
                event.addProperty("result", result.toString());
            }
        }
        
        log.debug("Task completed: taskId={}", taskId);
    }
    
    @Override
    public void onError(String taskId, Throwable error) {
        progressMap.compute(taskId, (id, existing) -> {
            if (existing == null) {
                return new Progress(taskId, 0, error.getMessage(), 
                    Progress.ProgressStatus.FAILED, System.currentTimeMillis(), System.currentTimeMillis());
            }
            return existing.failed(error.getMessage());
        });
        
        // Update workflow event
        WorkflowEvent event = executions.get(taskId);
        if (event != null) {
            event.setError(error.getMessage());
            event.setCompleted(true);
        }
        
        log.error("Task failed: taskId={}", taskId, error);
    }
    
    @Override
    public Optional<Progress> getProgress(String taskId) {
        return Optional.ofNullable(progressMap.get(taskId));
    }
    
    @Override
    public boolean isCancelled(String taskId) {
        Progress progress = progressMap.get(taskId);
        return progress != null && progress.status() == Progress.ProgressStatus.CANCELLED;
    }
    
    @Override
    public boolean cancelTask(String taskId) {
        Progress existing = progressMap.compute(taskId, (id, progress) -> {
            if (progress == null || 
                progress.status() == Progress.ProgressStatus.COMPLETED ||
                progress.status() == Progress.ProgressStatus.FAILED) {
                return progress; // Can't cancel if not exists or already finished
            }
            return new Progress(taskId, progress.percentComplete(), "Cancelled", 
                Progress.ProgressStatus.CANCELLED, progress.startTime(), System.currentTimeMillis());
        });
        
        boolean cancelled = existing != null && existing.status() == Progress.ProgressStatus.CANCELLED;
        
        if (cancelled) {
            // Update workflow event
            WorkflowEvent event = executions.get(taskId);
            if (event != null) {
                event.setError("Task cancelled");
                event.setCompleted(true);
            }
            log.info("Task cancelled: taskId={}", taskId);
        }
        
        return cancelled;
    }
}