package ai.driftkit.workflows.core.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for inline steps with expressions.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InlineStep {
    String expression() default "";    // The logic to execute

    String nextStep() default "";      // The default next step to execute

    String condition() default "";     // Condition to determine the next step

    String trueStep() default "";      // Next step if condition is true

    String falseStep() default "";     // Next step if condition is false

    int invocationLimit() default 5;

    OnInvocationsLimit onInvocationsLimit() default OnInvocationsLimit.STOP;
}
