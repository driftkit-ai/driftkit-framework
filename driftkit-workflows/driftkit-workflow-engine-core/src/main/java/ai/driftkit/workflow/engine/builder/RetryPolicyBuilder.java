package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.annotations.RetryPolicy;

import java.lang.annotation.Annotation;

/**
 * Builder for creating RetryPolicy instances programmatically.
 * This is useful when configuring retry behavior in fluent API without annotations.
 */
public class RetryPolicyBuilder {
    
    private int maxAttempts = 3;
    private long delay = 1000;
    private double backoffMultiplier = 1.0;
    private long maxDelay = 60000;
    private double jitterFactor = 0.1;
    private Class<? extends Throwable>[] retryOn = new Class[0];
    private Class<? extends Throwable>[] abortOn = new Class[0];
    private boolean retryOnFailResult = false;
    
    public static RetryPolicyBuilder retry() {
        return new RetryPolicyBuilder();
    }
    
    public RetryPolicyBuilder withMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }
    
    public RetryPolicyBuilder withDelay(long delay) {
        this.delay = delay;
        return this;
    }
    
    public RetryPolicyBuilder withBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
        return this;
    }
    
    public RetryPolicyBuilder withMaxDelay(long maxDelay) {
        this.maxDelay = maxDelay;
        return this;
    }
    
    public RetryPolicyBuilder withJitterFactor(double jitterFactor) {
        this.jitterFactor = jitterFactor;
        return this;
    }
    
    @SafeVarargs
    public final RetryPolicyBuilder withRetryOn(Class<? extends Throwable>... retryOn) {
        this.retryOn = retryOn;
        return this;
    }
    
    @SafeVarargs
    public final RetryPolicyBuilder withAbortOn(Class<? extends Throwable>... abortOn) {
        this.abortOn = abortOn;
        return this;
    }
    
    public RetryPolicyBuilder withRetryOnFailResult(boolean retryOnFailResult) {
        this.retryOnFailResult = retryOnFailResult;
        return this;
    }
    
    /**
     * Convenience method for exponential backoff with default settings.
     */
    public RetryPolicyBuilder exponentialBackoff() {
        return this.withBackoffMultiplier(2.0).withMaxDelay(30000);
    }
    
    /**
     * Convenience method for linear backoff (constant delay).
     */
    public RetryPolicyBuilder linearBackoff() {
        return this.withBackoffMultiplier(1.0);
    }
    
    /**
     * Builds the RetryPolicy instance.
     */
    public RetryPolicy build() {
        return new RetryPolicy() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RetryPolicy.class;
            }
            
            @Override
            public int maxAttempts() {
                return maxAttempts;
            }
            
            @Override
            public long delay() {
                return delay;
            }
            
            @Override
            public double backoffMultiplier() {
                return backoffMultiplier;
            }
            
            @Override
            public long maxDelay() {
                return maxDelay;
            }
            
            @Override
            public double jitterFactor() {
                return jitterFactor;
            }
            
            @Override
            public Class<? extends Throwable>[] retryOn() {
                return retryOn;
            }
            
            @Override
            public Class<? extends Throwable>[] abortOn() {
                return abortOn;
            }
            
            @Override
            public boolean retryOnFailResult() {
                return retryOnFailResult;
            }
        };
    }
}