package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.domain.AsyncStepState;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages asynchronous task execution for workflows.
 * This component handles async step execution, progress tracking,
 * and result handling.
 */
@Slf4j
@RequiredArgsConstructor
public class AsyncTaskManager {
    
    private final ExecutorService executorService;
    private final ProgressTracker progressTracker;
    private final WorkflowStateRepository stateRepository;
    private final AsyncStepHandler asyncStepHandler;
    private final Map<String, AsyncTaskInfo> runningTasks = new ConcurrentHashMap<>();
    
    /**
     * Handles an async step result.
     */
    public CompletableFuture<StepResult<?>> handleAsyncStep(
            WorkflowInstance instance,
            WorkflowGraph<?, ?> graph,
            String stepId,
            StepResult.Async<?> asyncResult) {
        
        String taskId = asyncResult.taskId();
        Object immediateData = asyncResult.immediateData();
        
        // Create async state
        AsyncStepState asyncState = AsyncStepState.started(taskId, immediateData);
        instance.setAsyncStepState(stepId, asyncState);
        
        // Create initial event for tracking
        WorkflowEvent initialEvent = immediateData instanceof WorkflowEvent ? 
            (WorkflowEvent) immediateData : 
            WorkflowEvent.asyncStarted(taskId, "");
        
        // Track the execution
        progressTracker.trackExecution(taskId, initialEvent);
        
        // Create progress reporter
        AsyncProgressReporter progressReporter = createProgressReporter(
            instance.getInstanceId(), stepId, taskId);
        
        // Check if taskArgs contains a CompletableFuture
        Object futureObj = asyncResult.taskArgs().get(WorkflowContext.Keys.ASYNC_FUTURE);
        if (futureObj instanceof CompletableFuture<?>) {
            // Handle CompletableFuture-based async operation
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> future = (CompletableFuture<Object>) futureObj;
            
            return future.handle((result, error) -> {
                StepResult<?> handlerResult;
                if (error != null) {
                    log.error("Async task {} failed for step {}", taskId, stepId, error);
                    handlerResult = new StepResult.Fail<>(error);
                } else {
                    log.debug("Async task {} completed for step {}", taskId, stepId);
                    // Check if result is already a StepResult
                    if (result instanceof StepResult<?>) {
                        handlerResult = (StepResult<?>) result;
                    } else {
                        // Check if current step has any outgoing edges (next steps)
                        var node = graph.nodes().get(stepId);
                        boolean isFinalStep = node != null && graph.getOutgoingEdges(stepId).isEmpty();
                        
                        if (isFinalStep) {
                            // This is a final step, wrap as Finish
                            handlerResult = new StepResult.Finish<>(result);
                        } else {
                            // Not a final step, wrap as Continue
                            handlerResult = new StepResult.Continue<>(result);
                        }
                    }
                }
                
                // Update async state with completion
                updateAsyncState(instance.getInstanceId(), stepId, state -> {
                    state.complete(handlerResult);
                    WorkflowEvent completedEvent = WorkflowEvent.completed(Map.of(
                        "taskId", taskId,
                        "status", "completed"
                    ));
                    progressTracker.updateExecutionStatus(taskId, completedEvent);
                });
                
                return handlerResult;
            });
        }
        
        // Create async task for traditional async handlers
        Supplier<StepResult<?>> asyncTask = () -> {
            try {
                log.debug("Executing async task {} for step {}", taskId, stepId);
                
                // Execute the async handler
                StepResult<?> handlerResult = asyncStepHandler.handleAsyncResult(
                    graph,
                    taskId,
                    stepId,
                    asyncResult.taskArgs(),
                    instance.getContext(),
                    progressReporter
                );
                
                // Update async state with completion
                updateAsyncState(instance.getInstanceId(), stepId, state -> {
                    state.complete(handlerResult);
                    WorkflowEvent completedEvent = WorkflowEvent.completed(Map.of(
                        "taskId", taskId,
                        "status", "completed"
                    ));
                    progressTracker.updateExecutionStatus(taskId, completedEvent);
                });
                
                return handlerResult != null ? handlerResult : new StepResult.Continue<>(null);
                
            } catch (Exception e) {
                log.error("Async task {} failed for step {}", taskId, stepId, e);
                updateAsyncState(instance.getInstanceId(), stepId, state -> state.fail(e));
                progressTracker.onError(taskId, e);
                throw new RuntimeException("Async execution failed", e);
            }
        };
        
        // Execute async task
        CompletableFuture<StepResult<?>> future = CompletableFuture
            .supplyAsync(asyncTask, executorService)
            .exceptionally(error -> {
                log.error("Async task {} completed with error", taskId, error);
                return new StepResult.Fail<>(error);
            });
        
        // Store task info
        runningTasks.put(taskId, new AsyncTaskInfo(
            instance.getInstanceId(), stepId, taskId, future
        ));
        
        // Clean up on completion
        future.whenComplete((result, error) -> {
            runningTasks.remove(taskId);
            if (error == null) {
                log.debug("Async task {} completed successfully", taskId);
                progressTracker.onComplete(taskId, result);
            }
        });
        
        return future;
    }
    
    /**
     * Cancels an async task if it's running.
     */
    public boolean cancelAsyncTask(String instanceId, String stepId) {
        var tasksToCancel = runningTasks.values().stream()
            .filter(task -> task.instanceId.equals(instanceId) && 
                           task.stepId.equals(stepId))
            .toList();
        
        boolean anyCancelled = false;
        for (var task : tasksToCancel) {
            if (task.future.cancel(true)) {
                anyCancelled = true;
                runningTasks.remove(task.taskId);
                progressTracker.onError(task.taskId, 
                    new RuntimeException("Task cancelled"));
            }
        }
        
        return anyCancelled;
    }
    
    /**
     * Gets the current progress of an async task.
     */
    public Optional<WorkflowEvent> getAsyncProgress(String instanceId, String stepId) {
        return runningTasks.values().stream()
            .filter(task -> task.instanceId.equals(instanceId) && 
                           task.stepId.equals(stepId))
            .findFirst()
            .flatMap(task -> progressTracker.getProgress(task.taskId))
            .map(progress -> WorkflowEvent.withProgress(
                progress.percentComplete(), 
                progress.message()
            ));
    }
    
    /**
     * Creates a progress reporter for async tasks.
     */
    private AsyncProgressReporter createProgressReporter(
            String instanceId, String stepId, String taskId) {
        
        return new AsyncProgressReporter() {
            @Override
            public void updateProgress(int percentComplete, String message) {
                progressTracker.updateProgress(taskId, percentComplete, message);
            }
            
            @Override
            public boolean isCancelled() {
                return !runningTasks.containsKey(taskId);
            }
        };
    }
    
    /**
     * Updates async state for a step.
     */
    private void updateAsyncState(String instanceId, String stepId, 
                                 Consumer<AsyncStepState> updater) {
        // This would need to be implemented with proper state loading/saving
        // For now, we'll assume the state is updated in the instance
        log.debug("Updating async state for instance {} step {}", instanceId, stepId);
    }
    
    /**
     * Cancels all async tasks for a workflow instance.
     * 
     * @param instanceId The workflow instance ID
     * @return true if any tasks were cancelled, false otherwise
     */
    public boolean cancelAsyncTasks(String instanceId) {
        var tasksToCancel = runningTasks.values().stream()
            .filter(task -> task.instanceId.equals(instanceId))
            .toList();
        
        boolean anyCancelled = false;
        for (var task : tasksToCancel) {
            if (task.future.cancel(true)) {
                runningTasks.remove(task.taskId);
                progressTracker.onError(task.taskId, 
                    new RuntimeException("Workflow cancelled"));
                log.info("Cancelled async task {} for instance {}", 
                    task.taskId, instanceId);
                anyCancelled = true;
            }
        }
        return anyCancelled;
    }
    
    /**
     * Information about a running async task.
     */
    private record AsyncTaskInfo(
        String instanceId,
        String stepId,
        String taskId,
        CompletableFuture<StepResult<?>> future
    ) {}
}