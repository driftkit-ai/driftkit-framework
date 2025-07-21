package ai.driftkit.chat.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for asynchronous workflow step methods.
 * This is used to identify methods that handle the asynchronous part of a workflow step.
 * These methods will be called after the main step method returns an AsyncTaskEvent.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AsyncStep {
    /**
     * The ID of the step that this async method is associated with.
     * This must match the ID of a step defined with @WorkflowStep.
     */
    String forStep();
    
    /**
     * Human-readable description of the async step.
     */
    String description() default "";
    
    /**
     * The ID of the schema for input data.
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
     * The class to use as output schema.
     * This class will be automatically converted to an AIFunctionSchema.
     * If specified, takes precedence over outputSchemaId.
     */
    Class<?> outputClass() default void.class;
    
    /**
     * The classes to use as possible output schemas.
     * These classes will be automatically converted to AIFunctionSchema objects.
     * If specified, takes precedence over outputSchemaId and outputClass.
     */
    Class<?>[] nextClasses() default {};
}