package ai.driftkit.workflow.engine.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a schema object as a system message.
 * When applied to a class, the generated ChatMessageTask will have the system flag set to true.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaSystem {
    /**
     * Whether this schema represents a system message.
     * Default is true when the annotation is present.
     * 
     * @return true if this is a system message, false otherwise
     */
    boolean value() default true;
}