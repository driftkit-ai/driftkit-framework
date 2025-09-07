package ai.driftkit.workflow.test.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for mock injection.
 * This annotation is used as a compatibility layer with Mockito's @Mock.
 * When Mockito is not available, this provides similar functionality.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mock {
    /**
     * Optional name for the mock.
     */
    String name() default "";
    
    /**
     * Extra interfaces to mock.
     */
    Class<?>[] extraInterfaces() default {};
}