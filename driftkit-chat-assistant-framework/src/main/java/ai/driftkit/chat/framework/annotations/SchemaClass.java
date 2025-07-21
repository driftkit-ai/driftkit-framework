package ai.driftkit.chat.framework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a schema class for workflow steps.
 * The framework will automatically convert this class to AIFunctionSchema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SchemaClass {
    /**
     * The ID of the schema. If not provided, class name will be used.
     * @return schema ID
     */
    String id() default "";
    
    /**
     * Description of the schema class
     * @return description
     */
    String description() default "";
    
    /**
     * Flag indicating this schema is composable and should be broken into separate questions
     * when processing in the workflow. When true, each field becomes a separate form/question.
     * @return true if schema should be handled as composable, false otherwise
     */
    boolean composable() default false;
}