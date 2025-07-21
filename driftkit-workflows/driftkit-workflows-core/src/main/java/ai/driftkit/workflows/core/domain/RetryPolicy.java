package ai.driftkit.workflows.core.domain;

public @interface RetryPolicy {
    int delay() default 5;

    int maximumAttempts() default 10;
}
