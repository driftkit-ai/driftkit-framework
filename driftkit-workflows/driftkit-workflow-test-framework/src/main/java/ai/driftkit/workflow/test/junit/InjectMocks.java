package ai.driftkit.workflow.test.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for automatic mock injection.
 * All @Mock annotated fields in the test class will be injected
 * into matching fields in the target object.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectMocks {
    /**
     * Whether to inject only explicitly matched fields.
     */
    boolean strictMatching() default false;
}