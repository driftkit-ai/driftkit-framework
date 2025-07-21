package ai.driftkit.chat.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide additional information about a workflow step.
 * This is used to document steps and their parameters.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StepInfo {
    /**
     * Detailed description of the step
     */
    String description() default "";
    
    /**
     * Maximum number of times this step can be invoked
     */
    int invocationsLimit() default Integer.MAX_VALUE;
    
    /**
     * What to do when invocations limit is reached
     */
    OnInvocationsLimit onInvocationsLimit() default OnInvocationsLimit.ERROR;
    
    /**
     * Whether this step can be executed asynchronously
     */
    boolean async() default false;
    
    /**
     * Whether user input is required for this step
     */
    boolean userInputRequired() default false;
}