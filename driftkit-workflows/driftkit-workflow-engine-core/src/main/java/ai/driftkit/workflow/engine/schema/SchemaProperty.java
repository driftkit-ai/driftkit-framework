package ai.driftkit.workflow.engine.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to provide detailed metadata for a property in the AI function schema.
 * This helps AI models understand the constraints and expectations for each field.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaProperty {
    /**
     * Whether this property is required.
     * 
     * @return true if the property is required
     */
    boolean required() default false;
    
    /**
     * Description of the property for AI understanding.
     * 
     * @return The property description
     */
    String description() default "";
    
    /**
     * Optional name ID for localization or reference.
     * 
     * @return The name ID
     */
    String nameId() default "";
    
    /**
     * Allowed values for this property (for enums or constrained fields).
     * 
     * @return Array of allowed values
     */
    String[] values() default {};
    
    /**
     * Minimum value (for numeric types).
     * 
     * @return The minimum value as a string
     */
    String min() default "";
    
    /**
     * Maximum value (for numeric types).
     * 
     * @return The maximum value as a string
     */
    String max() default "";
    
    /**
     * Pattern for string validation (regex).
     * 
     * @return The validation pattern
     */
    String pattern() default "";
}