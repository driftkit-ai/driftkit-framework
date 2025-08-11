package ai.driftkit.workflow.engine.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide a description for a class or field in the AI function schema.
 * This description helps AI models understand the purpose and expected content.
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaDescription {
    /**
     * The description text for the schema element.
     * 
     * @return The description
     */
    String value();
}