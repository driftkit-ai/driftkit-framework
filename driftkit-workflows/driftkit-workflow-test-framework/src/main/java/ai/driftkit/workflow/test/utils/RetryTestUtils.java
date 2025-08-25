package ai.driftkit.workflow.test.utils;

import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.test.core.TestExecutionInterceptor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Utilities for testing retry behavior in workflows.
 * Provides helpers to simulate failures and verify retry attempts.
 */
@Slf4j
public class RetryTestUtils {
    
    /**
     * Assert that a step was retried the expected number of times.
     * 
     * @param interceptor The test execution interceptor
     * @param workflowId The workflow ID
     * @param stepId The step ID
     * @param expectedAttempts The expected number of attempts (including initial)
     */
    public static void assertRetryAttempts(TestExecutionInterceptor interceptor,
                                          String workflowId, String stepId,
                                          int expectedAttempts) {
        List<TestExecutionInterceptor.ExecutionRecord> history = 
            interceptor.getStepHistory(workflowId, stepId);
        
        // Count only execution starts
        long attempts = history.stream()
            .filter(TestExecutionInterceptor.ExecutionRecord::isStart)
            .count();
        
        assertEquals(expectedAttempts, attempts,
            String.format("Expected %d retry attempts for step %s.%s but got %d",
                expectedAttempts, workflowId, stepId, attempts));
        
        log.debug("Verified {} retry attempts for {}.{}", attempts, workflowId, stepId);
    }
    
    /**
     * Assert that retries happened with expected delays.
     * 
     * @param interceptor The test execution interceptor
     * @param workflowId The workflow ID
     * @param stepId The step ID
     * @param expectedDelays Expected delays between retries (with tolerance)
     * @param toleranceMs Tolerance in milliseconds for delay verification
     */
    public static void assertRetryDelays(TestExecutionInterceptor interceptor,
                                        String workflowId, String stepId,
                                        long[] expectedDelays, long toleranceMs) {
        List<TestExecutionInterceptor.ExecutionRecord> history = 
            interceptor.getStepHistory(workflowId, stepId);
        
        // Get execution start times
        List<Long> startTimes = history.stream()
            .filter(TestExecutionInterceptor.ExecutionRecord::isStart)
            .map(record -> record.timestamp().toEpochMilli())
            .toList();
        
        assertTrue(startTimes.size() >= expectedDelays.length + 1,
            "Not enough retry attempts to verify delays");
        
        // Verify delays between attempts
        for (int i = 0; i < expectedDelays.length; i++) {
            long actualDelay = startTimes.get(i + 1) - startTimes.get(i);
            long expectedDelay = expectedDelays[i];
            
            assertTrue(Math.abs(actualDelay - expectedDelay) <= toleranceMs,
                String.format("Retry delay %d was %dms, expected %dms (±%dms)",
                    i + 1, actualDelay, expectedDelay, toleranceMs));
        }
    }
    
    /**
     * Set up a step to fail a specific number of times before succeeding.
     * 
     * @param interceptor The test execution interceptor
     * @param workflowId The workflow ID
     * @param stepId The step ID
     * @param failureCount Number of times to fail before succeeding
     * @param successResult The result to return on success
     */
    public static <T> void setupFailingStep(TestExecutionInterceptor interceptor,
                                           String workflowId, String stepId,
                                           int failureCount, T successResult) {
        AtomicInteger attempts = new AtomicInteger(0);
        
        interceptor.addStepMock(workflowId, stepId, (Object input) -> {
            int attempt = attempts.incrementAndGet();
            if (attempt <= failureCount) {
                log.debug("Simulating failure #{} for {}.{}", attempt, workflowId, stepId);
                throw new RuntimeException(
                    String.format("Simulated failure #%d for step %s", attempt, stepId));
            }
            log.debug("Returning success for {}.{} on attempt {}", workflowId, stepId, attempt);
            return StepResult.finish(successResult);
        });
    }
    
    /**
     * Set up a step to fail with different exceptions.
     * 
     * @param interceptor The test execution interceptor
     * @param workflowId The workflow ID
     * @param stepId The step ID
     * @param exceptions Exceptions to throw in sequence
     * @param successResult The result to return after all exceptions
     */
    public static <T> void setupFailingStepWithExceptions(TestExecutionInterceptor interceptor,
                                                         String workflowId, String stepId,
                                                         Exception[] exceptions,
                                                         T successResult) {
        AtomicInteger attempts = new AtomicInteger(0);
        
        interceptor.addStepMock(workflowId, stepId, (Object input) -> {
            int attempt = attempts.getAndIncrement();
            if (attempt < exceptions.length) {
                Exception ex = exceptions[attempt];
                log.debug("Throwing exception {} for {}.{}", 
                    ex.getClass().getSimpleName(), workflowId, stepId);
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else {
                    throw new RuntimeException(ex);
                }
            }
            return StepResult.finish(successResult);
        });
    }
    
    /**
     * Set up a step that fails based on input conditions.
     * 
     * @param interceptor The test execution interceptor
     * @param workflowId The workflow ID
     * @param stepId The step ID
     * @param failureCondition Function that determines if step should fail
     * @param successResult The result to return on success
     */
    public static <I, T> void setupConditionalFailingStep(TestExecutionInterceptor interceptor,
                                                         String workflowId, String stepId,
                                                         Function<I, Boolean> failureCondition,
                                                         T successResult) {
        interceptor.addStepMock(workflowId, stepId, (I input) -> {
            if (failureCondition.apply(input)) {
                throw new RuntimeException("Conditional failure for input: " + input);
            }
            return StepResult.finish(successResult);
        });
    }
    
    /**
     * Set up a step that alternates between success and failure.
     * 
     * @param interceptor The test execution interceptor
     * @param workflowId The workflow ID
     * @param stepId The step ID
     * @param successResult The result to return on success
     * @param failureMessage The error message for failures
     */
    public static <T> void setupAlternatingStep(TestExecutionInterceptor interceptor,
                                               String workflowId, String stepId,
                                               T successResult, String failureMessage) {
        AtomicInteger attempts = new AtomicInteger(0);
        
        interceptor.addStepMock(workflowId, stepId, (Object input) -> {
            int attempt = attempts.incrementAndGet();
            if (attempt % 2 == 1) {
                throw new RuntimeException(failureMessage);
            }
            return StepResult.finish(successResult);
        });
    }
    
    /**
     * Verify that a step eventually succeeded after retries.
     * 
     * @param interceptor The test execution interceptor
     * @param workflowId The workflow ID
     * @param stepId The step ID
     * @return The successful result from the last attempt
     */
    public static Object assertEventualSuccess(TestExecutionInterceptor interceptor,
                                              String workflowId, String stepId) {
        List<TestExecutionInterceptor.ExecutionRecord> history = 
            interceptor.getStepHistory(workflowId, stepId);
        
        // Find the last execution that completed
        TestExecutionInterceptor.ExecutionRecord lastComplete = history.stream()
            .filter(record -> !record.isStart() && record.result() != null)
            .reduce((first, second) -> second)
            .orElse(null);
        
        assertNotNull(lastComplete, 
            String.format("Step %s.%s never completed successfully", workflowId, stepId));
        
        StepResult<?> result = lastComplete.result();
        assertFalse(result instanceof StepResult.Fail,
            String.format("Step %s.%s ended in failure: %s", 
                workflowId, stepId, 
                result instanceof StepResult.Fail ? ((StepResult.Fail<?>) result).error() : ""));
        
        return result instanceof StepResult.Finish ? 
            ((StepResult.Finish<?>) result).result() : 
            result instanceof StepResult.Continue ? 
                ((StepResult.Continue<?>) result).data() : null;
    }
    
    /**
     * Create a retry simulator for testing exponential backoff.
     * 
     * @param baseDelay Base delay in milliseconds
     * @param multiplier Backoff multiplier
     * @param maxDelay Maximum delay in milliseconds
     * @return Array of expected delays
     */
    public static long[] calculateExponentialBackoff(long baseDelay, double multiplier,
                                                     long maxDelay, int attempts) {
        long[] delays = new long[attempts - 1];
        long currentDelay = baseDelay;
        
        for (int i = 0; i < delays.length; i++) {
            delays[i] = Math.min(currentDelay, maxDelay);
            currentDelay = (long) (currentDelay * multiplier);
        }
        
        return delays;
    }
    
    /**
     * Helper to create a step that tracks retry attempts.
     * 
     * @param successAfter Number of attempts before success
     * @param onAttempt Callback for each attempt
     * @return Step function
     */
    public static <I, O> Function<I, StepResult<O>> createRetryTrackingStep(
            int successAfter, O successResult,
            RetryAttemptCallback<I> onAttempt) {
        AtomicInteger attempts = new AtomicInteger(0);
        
        return input -> {
            int attempt = attempts.incrementAndGet();
            if (onAttempt != null) {
                onAttempt.onAttempt(attempt, input);
            }
            
            if (attempt < successAfter) {
                throw new RuntimeException("Retry attempt " + attempt);
            }
            
            return StepResult.finish(successResult);
        };
    }
    
    /**
     * Callback interface for retry attempts.
     */
    @FunctionalInterface
    public interface RetryAttemptCallback<I> {
        void onAttempt(int attemptNumber, I input);
    }
}