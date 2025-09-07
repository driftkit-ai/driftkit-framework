package ai.driftkit.workflow.test.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a workflow step mock.
 * The field must be a Mockito mock that will be registered
 * to handle specific workflow step execution.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkflowMock {
    /**
     * The workflow ID this mock applies to.
     */
    String workflow();
    
    /**
     * The step ID this mock applies to.
     */
    String step();
    
    /**
     * Optional description for documentation.
     */
    String description() default "";
}