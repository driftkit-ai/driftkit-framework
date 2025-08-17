package ai.driftkit.workflow.engine.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects and provides metrics about retry operations.
 * Thread-safe implementation for concurrent workflow execution.
 */
@Slf4j
public class RetryMetrics {
    
    private final ConcurrentHashMap<String, StepMetrics> stepMetrics = new ConcurrentHashMap<>();
    private final LongAdder totalRetryAttempts = new LongAdder();
    private final LongAdder totalRetrySuccesses = new LongAdder();
    private final LongAdder totalRetryFailures = new LongAdder();
    private final LongAdder totalRetryExhausted = new LongAdder();
    
    /**
     * Records a retry attempt.
     * 
     * @param stepId The step being retried
     * @param attemptNumber The attempt number
     * @param delayMs The delay before this attempt
     */
    public void recordRetryAttempt(String stepId, int attemptNumber, long delayMs) {
        StepMetrics metrics = stepMetrics.computeIfAbsent(stepId, k -> new StepMetrics());
        metrics.recordAttempt(attemptNumber, delayMs);
        totalRetryAttempts.increment();
    }
    
    /**
     * Records a successful retry.
     * 
     * @param stepId The step that succeeded
     * @param totalAttempts Total number of attempts it took
     * @param totalDurationMs Total time spent retrying
     */
    public void recordRetrySuccess(String stepId, int totalAttempts, long totalDurationMs) {
        StepMetrics metrics = stepMetrics.computeIfAbsent(stepId, k -> new StepMetrics());
        metrics.recordSuccess(totalAttempts, totalDurationMs);
        totalRetrySuccesses.increment();
    }
    
    /**
     * Records a retry failure.
     * 
     * @param stepId The step that failed
     * @param attemptNumber The attempt that failed
     * @param exception The exception that occurred
     */
    public void recordRetryFailure(String stepId, int attemptNumber, Exception exception) {
        StepMetrics metrics = stepMetrics.computeIfAbsent(stepId, k -> new StepMetrics());
        metrics.recordFailure(attemptNumber, exception);
        totalRetryFailures.increment();
    }
    
    /**
     * Records when retries are exhausted.
     * 
     * @param stepId The step that exhausted retries
     * @param totalAttempts Total attempts made
     */
    public void recordRetryExhausted(String stepId, int totalAttempts) {
        StepMetrics metrics = stepMetrics.computeIfAbsent(stepId, k -> new StepMetrics());
        metrics.recordExhausted(totalAttempts);
        totalRetryExhausted.increment();
    }
    
    /**
     * Gets metrics for a specific step.
     * 
     * @param stepId The step ID
     * @return Step metrics or null if no metrics exist
     */
    public StepMetrics getStepMetrics(String stepId) {
        return stepMetrics.get(stepId);
    }
    
    /**
     * Gets global retry metrics.
     * 
     * @return Global metrics summary
     */
    public GlobalMetrics getGlobalMetrics() {
        return new GlobalMetrics(
            totalRetryAttempts.sum(),
            totalRetrySuccesses.sum(),
            totalRetryFailures.sum(),
            totalRetryExhausted.sum(),
            stepMetrics.size()
        );
    }
    
    /**
     * Resets metrics for a specific step.
     * 
     * @param stepId The step ID
     */
    public void resetStep(String stepId) {
        stepMetrics.remove(stepId);
    }
    
    /**
     * Resets all metrics.
     */
    public void resetAll() {
        stepMetrics.clear();
        totalRetryAttempts.reset();
        totalRetrySuccesses.reset();
        totalRetryFailures.reset();
        totalRetryExhausted.reset();
    }
    
    /**
     * Metrics for a specific step.
     */
    @Getter
    public static class StepMetrics {
        private final AtomicInteger attemptCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger exhaustedCount = new AtomicInteger(0);
        private final AtomicLong totalRetryDelayMs = new AtomicLong(0);
        private final AtomicLong totalRetryDurationMs = new AtomicLong(0);
        private final AtomicInteger maxAttempts = new AtomicInteger(0);
        private final ConcurrentHashMap<String, AtomicInteger> exceptionCounts = new ConcurrentHashMap<>();
        
        void recordAttempt(int attemptNumber, long delayMs) {
            attemptCount.incrementAndGet();
            totalRetryDelayMs.addAndGet(delayMs);
            maxAttempts.updateAndGet(current -> Math.max(current, attemptNumber));
        }
        
        void recordSuccess(int totalAttempts, long totalDurationMs) {
            successCount.incrementAndGet();
            totalRetryDurationMs.addAndGet(totalDurationMs);
            maxAttempts.updateAndGet(current -> Math.max(current, totalAttempts));
        }
        
        void recordFailure(int attemptNumber, Exception exception) {
            failureCount.incrementAndGet();
            String exceptionType = exception.getClass().getSimpleName();
            exceptionCounts.computeIfAbsent(exceptionType, k -> new AtomicInteger(0))
                          .incrementAndGet();
        }
        
        void recordExhausted(int totalAttempts) {
            exhaustedCount.incrementAndGet();
            maxAttempts.updateAndGet(current -> Math.max(current, totalAttempts));
        }
        
        /**
         * Gets the success rate as a percentage.
         * 
         * @return Success rate (0-100) or -1 if no executions
         */
        public double getSuccessRate() {
            int total = successCount.get() + exhaustedCount.get();
            if (total == 0) {
                return -1;
            }
            return (double) successCount.get() / total * 100;
        }
        
        /**
         * Gets average retry delay in milliseconds.
         * 
         * @return Average delay or 0 if no retries
         */
        public double getAverageRetryDelay() {
            int attempts = attemptCount.get();
            if (attempts == 0) {
                return 0;
            }
            return (double) totalRetryDelayMs.get() / attempts;
        }
    }
    
    /**
     * Global retry metrics summary.
     */
    @Getter
    public static class GlobalMetrics {
        private final long totalAttempts;
        private final long totalSuccesses;
        private final long totalFailures;
        private final long totalExhausted;
        private final int uniqueSteps;
        
        GlobalMetrics(long totalAttempts, long totalSuccesses, long totalFailures,
                     long totalExhausted, int uniqueSteps) {
            this.totalAttempts = totalAttempts;
            this.totalSuccesses = totalSuccesses;
            this.totalFailures = totalFailures;
            this.totalExhausted = totalExhausted;
            this.uniqueSteps = uniqueSteps;
        }
        
        /**
         * Gets the overall success rate.
         * 
         * @return Success rate (0-100) or -1 if no executions
         */
        public double getOverallSuccessRate() {
            long total = totalSuccesses + totalExhausted;
            if (total == 0) {
                return -1;
            }
            return (double) totalSuccesses / total * 100;
        }
    }
}