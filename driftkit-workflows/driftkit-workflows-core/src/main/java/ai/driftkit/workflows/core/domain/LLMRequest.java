package ai.driftkit.workflows.core.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods representing LLM requests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LLMRequest {
    String prompt();                   // The prompt to send to the LLM

    String modelName();                // The model to use for the LLM request

    String nextStep() default "";      // The default next step to execute after this LLM request

    String condition() default "";     // Condition to determine the next step

    String trueStep() default "";      // Next step if condition is true

    String falseStep() default "";     // Next step if condition is false
}
