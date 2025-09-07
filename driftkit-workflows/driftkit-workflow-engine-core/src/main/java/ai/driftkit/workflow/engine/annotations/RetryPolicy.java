package ai.driftkit.workflow.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines retry behavior for workflow steps.
 * This annotation can be used within the @Step annotation to configure
 * how failed step executions should be retried.
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * @Step(
 *     retryPolicy = @RetryPolicy(
 *         maxAttempts = 3,
 *         delay = 1000,
 *         backoffMultiplier = 2.0,
 *         maxDelay = 10000
 *     )
 * )
 * public StepResult<Result> processStep(Input input) {
 *     // Step logic that might fail
 * }
 * }</pre>
 */
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RetryPolicy {
    /**
     * Maximum number of retry attempts.
     * Default is 3 attempts (initial + 2 retries).
     * Set to 1 to disable retries.
     * 
     * @return Maximum retry attempts
     */
    int maxAttempts() default 3;
    
    /**
     * Initial delay between retries in milliseconds.
     * Default is 1000ms (1 second).
     * 
     * @return Initial delay in milliseconds
     */
    long delay() default 1000;
    
    /**
     * Multiplier for exponential backoff.
     * Each retry delay will be multiplied by this factor.
     * Default is 1.0 (no backoff, constant delay).
     * Set to 2.0 for exponential backoff (1s, 2s, 4s, 8s...).
     * 
     * @return Backoff multiplier
     */
    double backoffMultiplier() default 1.0;
    
    /**
     * Maximum delay between retries in milliseconds.
     * Prevents exponential backoff from growing indefinitely.
     * Default is 60000ms (1 minute).
     * 
     * @return Maximum delay in milliseconds
     */
    long maxDelay() default 60000;
    
    /**
     * Jitter factor for randomizing retry delays.
     * Value between 0.0 and 1.0, where 0.0 means no jitter
     * and 1.0 means up to 100% randomization.
     * Default is 0.1 (10% jitter).
     * 
     * @return Jitter factor
     */
    double jitterFactor() default 0.1;
    
    /**
     * Exception types that should trigger a retry.
     * If empty, all exceptions will trigger retry.
     * 
     * @return Array of retryable exception types
     */
    Class<? extends Throwable>[] retryOn() default {};
    
    /**
     * Exception types that should abort retry attempts.
     * These exceptions will not trigger retries even if they
     * are subclasses of exceptions in retryOn.
     * 
     * @return Array of non-retryable exception types
     */
    Class<? extends Throwable>[] abortOn() default {};
    
    /**
     * Whether to retry on StepResult.Fail results.
     * Default is false (only exceptions trigger retry).
     * 
     * @return True to retry on fail results
     */
    boolean retryOnFailResult() default false;
}