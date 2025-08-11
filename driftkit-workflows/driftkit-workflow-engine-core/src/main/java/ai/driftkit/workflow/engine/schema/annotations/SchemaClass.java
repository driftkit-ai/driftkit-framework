package ai.driftkit.workflow.engine.schema.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class for schema generation with additional metadata.
 * Ported from driftkit-chat-assistant-framework
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SchemaClass {
    /**
     * The unique identifier for this schema.
     */
    String id() default "";
    
    /**
     * Description of the schema.
     */
    String description() default "";
    
    /**
     * Whether this schema is composable (can be split into multiple parts).
     */
    boolean composable() default false;
}