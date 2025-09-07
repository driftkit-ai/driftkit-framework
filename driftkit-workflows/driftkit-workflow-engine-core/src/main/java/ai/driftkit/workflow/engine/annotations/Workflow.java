package ai.driftkit.workflow.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a workflow definition.
 * Classes annotated with @Workflow will be automatically discovered and registered
 * by the workflow engine during application startup.
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * @Workflow(id = "user-onboarding", version = "2.0")
 * public class UserOnboardingWorkflow {
 *     // workflow implementation
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Workflow {
    /**
     * Unique identifier for this workflow.
     * This ID is used to reference the workflow when starting execution.
     * 
     * @return The workflow ID
     */
    String id();
    
    /**
     * Version of the workflow definition.
     * Useful for managing workflow evolution and backward compatibility.
     * 
     * @return The workflow version (default: "1.0")
     */
    String version() default "1.0";
    
    /**
     * Human-readable description of what this workflow does.
     * 
     * @return The workflow description
     */
    String description() default "";
}