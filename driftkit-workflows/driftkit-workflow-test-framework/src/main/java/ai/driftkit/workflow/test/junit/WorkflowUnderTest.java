package ai.driftkit.workflow.test.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the workflow under test.
 * The extension will automatically register this workflow with the test engine
 * and inject any mocks declared in the test class.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowUnderTest {
    /**
     * Optional workflow ID. If not specified, will be derived from the class name.
     */
    String id() default "";
    
    /**
     * Whether to automatically inject @Mock annotated fields from test class.
     */
    boolean autoInjectMocks() default true;
}