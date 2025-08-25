package ai.driftkit.workflow.test.utils;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.async.ProgressTracker.Progress;
import ai.driftkit.workflow.engine.async.TaskProgressReporter;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utilities for testing asynchronous step execution in workflows.
 * Provides helpers to track progress, simulate async operations, and verify async behavior.
 */
@Slf4j
public class AsyncTestUtils {
    
    /**
     * Wait for an async task to complete.
     * 
     * @param tracker The progress tracker
     * @param taskId The async task ID
     * @param timeout Timeout duration
     * @return The final progress status
     */
    public static Progress waitForAsyncCompletion(ProgressTracker tracker,
                                                  String taskId,
                                                  Duration timeout) {
        return Awaitility.await()
            .atMost(timeout)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(
                () -> tracker.getProgress(taskId),
                progress -> progress.isPresent() && 
                           progress.get().status() == Progress.ProgressStatus.COMPLETED
            )
            .orElse(null);
    }
    
    /**
     * Assert that an async task reaches a specific progress percentage.
     * 
     * @param tracker The progress tracker
     * @param taskId The async task ID
     * @param expectedProgress Expected progress percentage (0-100)
     * @param timeout Timeout duration
     */
    public static void assertProgressReaches(ProgressTracker tracker,
                                           String taskId,
                                           int expectedProgress,
                                           Duration timeout) {
        Optional<Progress> finalProgress = Awaitility.await()
            .atMost(timeout)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(
                () -> tracker.getProgress(taskId),
                progress -> progress.isPresent() && 
                           progress.get().percentComplete() >= expectedProgress
            );
        
        assertTrue(finalProgress.isPresent() && 
                  finalProgress.get().percentComplete() >= expectedProgress,
            String.format("Expected progress >= %d%%, but was %d%%",
                expectedProgress, 
                finalProgress.map(Progress::percentComplete).orElse(0)));
    }
    
    /**
     * Create a tracking progress tracker that records all updates.
     * 
     * @param delegate The underlying progress tracker
     * @return A tracking progress tracker
     */
    public static TrackingProgressTracker createTrackingProgressTracker(ProgressTracker delegate) {
        return new TrackingProgressTracker(delegate);
    }
    
    /**
     * Create a mock async step that simulates progress.
     * 
     * @param progressUpdates Progress updates to simulate (percentage -> delay in ms)
     * @param result Final result to return
     * @return Function that returns an async StepResult
     */
    public static <T> Function<Object, StepResult<?>> createMockAsyncStep(
            Map<Integer, Long> progressUpdates,
            T result,
            String asyncStepId) {
        
        return input -> {
            // Create task args with progress simulation
            Map<String, Object> taskArgs = Map.of(
                "input", input,
                "progressUpdates", progressUpdates,
                "result", result
            );
            
            // Calculate total duration
            long totalDuration = progressUpdates.values().stream()
                .mapToLong(Long::longValue)
                .sum();
            
            return StepResult.async(asyncStepId, totalDuration, taskArgs);
        };
    }
    
    /**
     * Create an async step handler that simulates progress.
     * 
     * @return Async step handler function
     */
    public static <T> BiFunction<Map<String, Object>, TaskProgressReporter, StepResult<T>> 
            createProgressSimulator() {
        
        return (taskArgs, progressReporter) -> {
            @SuppressWarnings("unchecked")
            Map<Integer, Long> progressUpdates = 
                (Map<Integer, Long>) taskArgs.get("progressUpdates");
            @SuppressWarnings("unchecked")
            T result = (T) taskArgs.get("result");
            
            // Simulate progress updates
            if (progressUpdates != null) {
                progressUpdates.forEach((percentage, delay) -> {
                    try {
                        Thread.sleep(delay);
                        progressReporter.updateProgress(percentage, 
                            "Processing... " + percentage + "% complete");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            
            return StepResult.finish(result);
        };
    }
    
    /**
     * Verify that async step completes within timeout.
     * 
     * @param future The CompletableFuture to verify
     * @param timeout Timeout duration
     * @param expectedResult Expected result
     */
    public static <T> void assertAsyncCompletes(CompletableFuture<T> future,
                                               Duration timeout,
                                               T expectedResult) {
        try {
            T actualResult = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            assertEquals(expectedResult, actualResult,
                "Async operation completed with unexpected result");
        } catch (Exception e) {
            fail("Async operation failed to complete: " + e.getMessage());
        }
    }
    
    /**
     * Verify that async step fails with expected error.
     * 
     * @param future The CompletableFuture to verify
     * @param timeout Timeout duration
     * @param expectedError Expected error message
     */
    public static void assertAsyncFails(CompletableFuture<?> future,
                                       Duration timeout,
                                       String expectedError) {
        Exception exception = assertThrows(Exception.class, () -> 
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        );
        
        assertTrue(exception.getMessage().contains(expectedError),
            String.format("Expected error containing '%s' but got: %s",
                expectedError, exception.getMessage()));
    }
    
    /**
     * Create a mock async operation that can be controlled.
     * 
     * @return Controllable async operation
     */
    public static <T> ControllableAsyncOperation<T> createControllableAsync() {
        return new ControllableAsyncOperation<>();
    }
    
    /**
     * Controllable async operation for testing.
     */
    public static class ControllableAsyncOperation<T> {
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private final AtomicInteger progressValue = new AtomicInteger(0);
        
        public CompletableFuture<T> getFuture() {
            return future;
        }
        
        public void complete(T result) {
            progressValue.set(100);
            future.complete(result);
        }
        
        public void completeExceptionally(Throwable error) {
            future.completeExceptionally(error);
        }
        
        public void updateProgress(int percentage) {
            progressValue.set(percentage);
        }
        
        public int getProgress() {
            return progressValue.get();
        }
        
        public Function<Object, StepResult<?>> asAsyncStep(String asyncStepId) {
            return input -> StepResult.async(
                asyncStepId,
                10000, // 10 second estimate
                Map.of("operation", this, "input", input)
            );
        }
    }
    
    /**
     * Progress tracker that records all updates for verification.
     */
    public static class TrackingProgressTracker implements ProgressTracker {
        private final ProgressTracker delegate;
        private final Map<String, Progress> progressHistory = new ConcurrentHashMap<>();
        private final AtomicInteger updateCount = new AtomicInteger(0);
        
        public TrackingProgressTracker(ProgressTracker delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public String generateTaskId() {
            return delegate.generateTaskId();
        }
        
        @Override
        public void trackExecution(String taskId, WorkflowEvent event) {
            delegate.trackExecution(taskId, event);
        }
        
        @Override
        public void updateExecutionStatus(String taskId, WorkflowEvent event) {
            delegate.updateExecutionStatus(taskId, event);
        }
        
        @Override
        public void updateProgress(String taskId, int percentComplete, String message) {
            String key = taskId + "_" + updateCount.getAndIncrement();
            Progress progress = Progress.started(taskId).withUpdate(percentComplete, message);
            progressHistory.put(key, progress);
            AsyncTestUtils.log.debug("Progress update for {}: {}% - {}", taskId, percentComplete, message);
            delegate.updateProgress(taskId, percentComplete, message);
        }
        
        @Override
        public Optional<WorkflowEvent> getExecution(String taskId) {
            return delegate.getExecution(taskId);
        }
        
        @Override
        public void removeExecution(String taskId) {
            delegate.removeExecution(taskId);
        }
        
        @Override
        public <T> CompletableFuture<T> executeAsync(String taskId, WorkflowEvent initialEvent, Supplier<T> task) {
            return delegate.executeAsync(taskId, initialEvent, task);
        }
        
        @Override
        public void onComplete(String taskId, Object result) {
            delegate.onComplete(taskId, result);
        }
        
        @Override
        public void onError(String taskId, Throwable error) {
            delegate.onError(taskId, error);
        }
        
        @Override
        public Optional<Progress> getProgress(String taskId) {
            return delegate.getProgress(taskId);
        }
        
        @Override
        public boolean isCancelled(String taskId) {
            return delegate.isCancelled(taskId);
        }
        
        @Override
        public boolean cancelTask(String taskId) {
            return delegate.cancelTask(taskId);
        }
        
        public Map<String, Progress> getHistory() {
            return new ConcurrentHashMap<>(progressHistory);
        }
        
        public int getUpdateCount() {
            return updateCount.get();
        }
        
        public int getUpdateCount(String taskId) {
            return (int) progressHistory.keySet().stream()
                .filter(key -> key.startsWith(taskId + "_"))
                .count();
        }
    }
    
    /**
     * Assert that multiple async operations complete in order.
     * 
     * @param futures List of futures to verify
     * @param timeout Timeout for all operations
     */
    public static void assertAsyncCompletionOrder(CompletableFuture<?>[] futures,
                                                 Duration timeout) {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < futures.length; i++) {
            try {
                long remainingTimeout = timeout.toMillis() - 
                    (System.currentTimeMillis() - startTime);
                
                assertTrue(remainingTimeout > 0, 
                    "Timeout exceeded while waiting for async operation " + i);
                
                futures[i].get(remainingTimeout, TimeUnit.MILLISECONDS);
                AsyncTestUtils.log.debug("Async operation {} completed", i);
                
            } catch (Exception e) {
                fail(String.format("Async operation %d failed: %s", i, e.getMessage()));
            }
        }
    }
    
    /**
     * Create a simple async handler for testing.
     * 
     * @param result Result to return
     * @param delayMs Delay before completion
     * @return Async handler function
     */
    public static <T> BiFunction<Map<String, Object>, TaskProgressReporter, StepResult<T>> 
            createSimpleAsyncHandler(T result, long delayMs) {
        
        return (taskArgs, progressReporter) -> {
            try {
                progressReporter.updateProgress(0, "Starting...");
                Thread.sleep(delayMs / 2);
                progressReporter.updateProgress(50, "Half way done...");
                Thread.sleep(delayMs / 2);
                progressReporter.updateProgress(100, "Complete!");
                return StepResult.finish(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StepResult.fail("Interrupted");
            }
        };
    }
}