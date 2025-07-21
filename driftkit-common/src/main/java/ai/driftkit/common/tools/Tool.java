package ai.driftkit.common.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods as tool functions that can be called by language models.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    
    /**
     * Function name. If not specified, method name is used.
     */
    String name() default "";
    
    /**
     * Function description for the language model.
     */
    String description();
}