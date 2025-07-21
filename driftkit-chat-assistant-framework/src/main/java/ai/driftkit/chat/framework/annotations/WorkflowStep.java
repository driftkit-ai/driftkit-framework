package ai.driftkit.chat.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for workflow step methods.
 * This is used to identify and register methods as workflow steps.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WorkflowStep {
    int index() default 1;

    /**
     * The ID of the step.
     * If not specified:
     * 1. First tries to use the inputClass simple name
     * 2. Falls back to the method name if inputClass is not specified
     */
    String id() default "";

    /**
     * Human-readable description of the step.
     */
    String description() default "";

    /**
     * Indicates if this step requires user input.
     */
    boolean requiresUserInput() default false;

    /**
     * Indicates if this step should be executed asynchronously.
     */
    boolean async() default false;
    
    /**
     * The ID of the schema for input data.
     * This is only relevant if requiresUserInput is true.
     * 
     * Note: If inputClass is specified, this is ignored.
     */
    String inputSchemaId() default "";

    /**
     * The ID of the schema for output data.
     * 
     * Note: If outputClass is specified, this is ignored.
     */
    String outputSchemaId() default "";
    
    /**
     * The class to use as input schema.
     * This class will be automatically converted to an AIFunctionSchema.
     * If specified, takes precedence over inputSchemaId.
     */
    Class<?> inputClass() default void.class;
    
    /**
     * The classes to use as possible input schemas.
     * These classes will be automatically converted to AIFunctionSchema objects.
     * If specified, takes precedence over inputSchemaId and inputClass.
     */
    Class<?>[] inputClasses() default {};
    
    /**
     * The classes to use as possible output schemas (direct outputs of this step).
     * These classes will be automatically converted to AIFunctionSchema objects.
     */
    Class<?>[] outputClasses() default {};
    
    /**
     * The classes to use as possible next step input schemas.
     * These classes will be automatically converted to AIFunctionSchema objects.
     * If specified, takes precedence over outputSchemaId.
     */
    Class<?>[] nextClasses() default {};
    
    /**
     * Possible next steps this step can transition to.
     * If empty, framework will use the next step in the workflow definition order.
     */
    String[] nextSteps() default {};
    
    /**
     * Condition to evaluate before transitioning to next step.
     * Expression language can be used to reference input/output objects.
     */
    String condition() default "";
    
    /**
     * Step to execute if condition evaluates to true.
     */
    String onTrue() default "";
    
    /**
     * Step to execute if condition evaluates to false.
     */
    String onFalse() default "";
}