package ai.driftkit.workflow.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the initial step of a workflow.
 * Each workflow must have exactly one method annotated with @InitialStep.
 * This method will be the entry point when the workflow execution starts.
 * 
 * <p>The initial step method can have the following signatures:</p>
 * <ul>
 *   <li>{@code StepResult<?> methodName(InputType input)}</li>
 *   <li>{@code StepResult<?> methodName(InputType input, WorkflowContext context)}</li>
 *   <li>{@code CompletableFuture<StepResult<?>> methodName(InputType input)} (async)</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * @InitialStep
 * public StepResult<UserData> validateInput(RegistrationRequest request) {
 *     // validation logic
 *     return new Continue<>(userData);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InitialStep {
    /**
     * Optional description of what this initial step does.
     * If not provided, a default description will be generated.
     * 
     * @return The step description
     */
    String description() default "";
}