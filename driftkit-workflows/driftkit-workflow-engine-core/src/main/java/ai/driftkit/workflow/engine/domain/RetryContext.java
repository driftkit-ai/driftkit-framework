package ai.driftkit.workflow.engine.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Contains runtime information about retry attempts for a workflow step.
 * This context is available to steps during execution and provides
 * information about the current retry state.
 */
@Getter
@Builder
public class RetryContext {
    private final String stepId;
    private final int attemptNumber;
    private final int maxAttempts;
    
    @Singular
    private final List<RetryAttempt> previousAttempts;
    
    private final long firstAttemptTime;
    private final long currentAttemptTime;
    
    /**
     * Gets the number of retries remaining.
     * 
     * @return The remaining retry count
     */
    public int getRemainingRetries() {
        return Math.max(0, maxAttempts - attemptNumber);
    }
    
    /**
     * Checks if this is the first attempt (not a retry).
     * 
     * @return True if this is the first attempt
     */
    public boolean isFirstAttempt() {
        return attemptNumber == 1;
    }
    
    /**
     * Checks if this is the last attempt.
     * 
     * @return True if this is the last allowed attempt
     */
    public boolean isLastAttempt() {
        return attemptNumber >= maxAttempts;
    }
    
    /**
     * Gets the total elapsed time since the first attempt.
     * 
     * @return The elapsed duration in milliseconds
     */
    public long getTotalElapsedMs() {
        return currentAttemptTime - firstAttemptTime;
    }
    
    /**
     * Records a single retry attempt.
     */
    @Getter
    @Builder
    public static class RetryAttempt {
        private final int attemptNumber;
        private final long attemptTime;
        private final Throwable failure;
        private final long durationMs;
        
        public String getFailureMessage() {
            return failure != null ? failure.getMessage() : "Unknown failure";
        }
        
        public Class<? extends Throwable> getFailureType() {
            return failure != null ? failure.getClass() : null;
        }
    }
}