package ai.driftkit.workflows.core.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for workflow steps.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Step {
    String name() default "";

    RetryPolicy retryPolicy() default @RetryPolicy;

    int invocationLimit() default 5;

    OnInvocationsLimit onInvocationsLimit() default OnInvocationsLimit.STOP;

    String nextStep() default "";      // The default next step to execute
}
