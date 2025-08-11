package ai.driftkit.workflow.engine.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify a custom name for a class or field in the AI function schema.
 * This name will be used instead of the default class/field name in the generated schema.
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaName {
    /**
     * The custom name to use in the schema.
     * 
     * @return The schema name
     */
    String value();
}