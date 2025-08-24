package ai.driftkit.workflow.engine.core;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.async.TaskProgressReporter;
import ai.driftkit.workflow.engine.domain.AsyncStepState;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.AsyncStepStateRepository;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.WorkflowStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final AsyncStepStateRepository asyncStepStateRepository;
    private final Map<String, AsyncTaskInfo> runningTasks = new ConcurrentHashMap<>();
    
    /**
     * Handles an async step result with unified CompletableFuture approach.
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
        asyncStepStateRepository.save(asyncState);
        
        // Track the execution
        WorkflowEvent initialEvent = immediateData instanceof WorkflowEvent ? 
            (WorkflowEvent) immediateData : 
            WorkflowEvent.asyncStarted(taskId, "");
        progressTracker.trackExecution(taskId, initialEvent);
        
        // Create unified progress reporter
        TaskProgressReporter progressReporter = progressTracker.createReporter(taskId);
        
        // Convert to unified CompletableFuture handling
        CompletableFuture<StepResult<?>> future = createAsyncFuture(
            instance, graph, stepId, asyncResult, progressReporter);
        
        // Apply timeout if specified
        if (asyncResult.estimatedDurationMs() > 0) {
            future = future.orTimeout(asyncResult.estimatedDurationMs(), TimeUnit.MILLISECONDS)
                .exceptionally(error -> {
                    if (error instanceof TimeoutException) {
                        log.error("Async task {} timed out after {}ms", taskId, asyncResult.estimatedDurationMs());
                        return new StepResult.Fail<>(new RuntimeException("Async task timeout", error));
                    }
                    return new StepResult.Fail<>(error);
                });
        }
        
        // Handle completion uniformly
        CompletableFuture<StepResult<?>> resultFuture = future
            .whenComplete((result, error) -> {
                updateAsyncState(instance.getInstanceId(), stepId, state -> {
                    if (error != null) {
                        state.fail(error);
                        progressTracker.onError(taskId, error);
                    } else {
                        state.complete(result);
                        progressTracker.updateExecutionStatus(taskId, 
                            WorkflowEvent.completed(Map.of("taskId", taskId, "status", "completed")));
                    }
                });
            })
            .exceptionally(error -> {
                log.error("Async task {} failed", taskId, error);
                return new StepResult.Fail<>(error);
            });
        
        // Store task info
        runningTasks.put(taskId, new AsyncTaskInfo(
            instance.getInstanceId(), stepId, taskId, resultFuture
        ));
        
        // Clean up on completion
        resultFuture.whenComplete((result, error) -> {
            runningTasks.remove(taskId);
            if (error == null) {
                log.debug("Async task {} completed successfully", taskId);
                progressTracker.onComplete(taskId, result);
            }
        });
        
        return resultFuture;
    }
    
    /**
     * Creates a unified CompletableFuture for async execution.
     * Handles both CompletableFuture-based and traditional async handler approaches.
     */
    private CompletableFuture<StepResult<?>> createAsyncFuture(
            WorkflowInstance instance,
            WorkflowGraph<?, ?> graph,
            String stepId,
            StepResult.Async<?> asyncResult,
            TaskProgressReporter progressReporter) {
        
        String taskId = asyncResult.taskId();
        
        // Check if taskArgs contains a CompletableFuture
        Object futureObj = asyncResult.taskArgs().get(WorkflowContext.Keys.ASYNC_FUTURE);
        if (futureObj instanceof CompletableFuture<?>) {
            // Handle existing CompletableFuture
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> existingFuture = (CompletableFuture<Object>) futureObj;
            
            return existingFuture.thenApply(result -> {
                log.debug("Async future completed for task {}", taskId);
                return normalizeResult(result, graph, stepId);
            });
        }
        
        // Create CompletableFuture for traditional async handler
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing async handler for task {} on step {}", taskId, stepId);
                
                StepResult<?> handlerResult = asyncStepHandler.handleAsyncResult(
                    graph,
                    taskId,
                    stepId,
                    asyncResult.taskArgs(),
                    instance.getContext(),
                    progressReporter
                );
                
                if (handlerResult == null) {
                    throw new IllegalStateException(
                        "Async handler returned null result for task " + taskId + 
                        ". Async handlers must return a valid StepResult.");
                }
                
                return handlerResult;
                
            } catch (Exception e) {
                log.error("Async handler failed for task {}", taskId, e);
                throw new RuntimeException("Async execution failed", e);
            }
        }, executorService);
    }
    
    /**
     * Normalizes the result to ensure it's a StepResult.
     * Automatically wraps plain values and determines Continue vs Finish based on graph structure.
     */
    private StepResult<?> normalizeResult(Object result, WorkflowGraph<?, ?> graph, String stepId) {
        if (result instanceof StepResult<?>) {
            return (StepResult<?>) result;
        }
        
        // Check if current step has any outgoing edges
        var node = graph.nodes().get(stepId);
        boolean isFinalStep = node != null && graph.getOutgoingEdges(stepId).isEmpty();
        
        if (isFinalStep) {
            return new StepResult.Finish<>(result);
        } else {
            return new StepResult.Continue<>(result);
        }
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