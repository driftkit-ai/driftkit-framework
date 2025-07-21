package ai.driftkit.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a JSON schema should use strict mode.
 * In strict mode, all properties are required and additionalProperties is false.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonSchemaStrict {
    /**
     * Whether to enable strict mode for this schema.
     * @return true if strict mode should be enabled
     */
    boolean value() default true;
}