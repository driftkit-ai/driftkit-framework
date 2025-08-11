package ai.driftkit.workflow.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a workflow step.
 * Methods annotated with @Step will be included in the workflow graph
 * and can be targets of transitions from other steps.
 * 
 * <p>The step ID is determined in the following order:</p>
 * <ol>
 *   <li>Explicit value provided in the annotation</li>
 *   <li>Explicit id provided in the annotation</li>
 *   <li>Method name (if both value and id are empty)</li>
 * </ol>
 * 
 * <p>Step methods must return a StepResult type and can have various signatures:</p>
 * <ul>
 *   <li>{@code StepResult<?> methodName(InputType input)}</li>
 *   <li>{@code StepResult<?> methodName(InputType input, WorkflowContext context)}</li>
 *   <li>{@code StepResult<?> methodName(WorkflowContext context)} (for steps that only need context)</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * @Step  // ID will be "processPayment"
 * public StepResult<Receipt> processPayment(PaymentDetails details) {
 *     // process payment
 *     return new Continue<>(receipt);
 * }
 * 
 * @Step(value = "notify-user", requiresUserInput = true)  
 * public StepResult<Void> sendNotification(Receipt receipt, WorkflowContext context) {
 *     String userId = context.getStepResult("validateUser", String.class);
 *     // send notification
 *     return new Finish<>(null);
 * }
 * 
 * @Step(condition = "#result.success", onTrue = "processSuccess", onFalse = "handleError")
 * public StepResult<CheckResult> checkPayment(PaymentDetails details) {
 *     // check payment
 *     return new Branch<>(checkResult);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Step {
    /**
     * Alternative ID field for compatibility.
     * If both value and id are specified, value takes precedence.
     * 
     * @return The step ID (optional)
     */
    String id() default "";
    
    /**
     * Human-readable description of what this step does.
     * If not provided, a default description will be generated.
     * 
     * @return The step description
     */
    String description() default "";
    
    /**
     * Execution order index for this step.
     * Steps with lower indices are registered first in the workflow.
     * 
     * @return The execution order index
     */
    int index() default 0;
    
    /**
     * The expected input class for this step.
     * Used for type validation and schema generation.
     * 
     * @return The input class type
     */
    Class<?> inputClass() default void.class;
    
    /**
     * Possible next step IDs this step can transition to.
     * If empty, the workflow engine will determine next steps based on type matching.
     * 
     * @return Array of possible next step IDs
     */
    String[] nextSteps() default {};

    /**
     * The possible input classes for the next steps.
     * Used for type-based routing and schema generation.
     *
     * @return Array of possible next step input classes
     */
    Class<?>[] nextClasses() default {};

    /**
     * Spring Expression Language (SpEL) condition to evaluate for branching.
     * The condition has access to the step result and workflow context.
     * 
     * @return The condition expression
     */
    String condition() default "";
    
    /**
     * Step ID to execute if the condition evaluates to true.
     * Only used when condition is specified.
     * 
     * @return The step ID for true branch
     */
    String onTrue() default "";
    
    /**
     * Step ID to execute if the condition evaluates to false.
     * Only used when condition is specified.
     * 
     * @return The step ID for false branch
     */
    String onFalse() default "";

    /**
     * Timeout in milliseconds for async step execution.
     * Only applicable when async is true. -1 means no timeout.
     * 
     * @return The timeout in milliseconds
     */
    long timeoutMs() default -1;
}