package ai.driftkit.workflow.engine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * Marks a method as an asynchronous workflow step handler.
 * Async steps handle asynchronous operations such as long-running tasks, 
 * external API calls, or background processing.
 * 
 * <p>The async step is identified by its value, which must match the taskId 
 * used in StepResult.Async returned by a regular step.</p>
 * 
 * <p>Async step methods must return a StepResult type and have signatures like:</p>
 * <ul>
 *   <li>{@code StepResult<?> handleAsyncTask(Map<String, Object> taskArgs, WorkflowContext context, AsyncProgressReporter progress)}</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * @Step("process-order")
 * public StepResult<OrderTask> processOrder(OrderRequest request) {
 *     // Prepare async task
 *     Map<String, Object> taskArgs = Map.of("orderId", request.getOrderId());
 *     WorkflowEvent immediateEvent = WorkflowEvent.builder()
 *         .properties(Map.of("status", "PROCESSING"))
 *         .build();
 *     
 *     return new StepResult.Async<>(
 *         "process-order-async",  // taskId matching @AsyncStep value
 *         taskArgs,
 *         immediateEvent
 *     );
 * }
 * 
 * @AsyncStep("process-order-async")
 * public StepResult<OrderResult> processOrderAsync(Map<String, Object> taskArgs, 
 *                                                  WorkflowContext context,
 *                                                  AsyncProgressReporter progress) {
 *     // Handle async processing with progress updates
 *     String orderId = (String) taskArgs.get("orderId");
 *     
 *     progress.updateProgress(10, "Validating order");
 *     validateOrder(orderId);
 *     
 *     progress.updateProgress(50, "Processing payment");
 *     processPayment(orderId);
 *     
 *     progress.updateProgress(90, "Finalizing order");
 *     OrderResult result = orderService.finalizeOrder(orderId);
 *     
 *     return new StepResult.Continue<>(result);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AsyncStep {
    /**
     * The unique identifier for this async step.
     * This must match the taskId used in StepResult.Async.
     * 
     * @return The async step ID
     */
    String value();
    
    /**
     * Human-readable description of what this async step handles.
     * 
     * @return The step description
     */
    String description() default "";
    
    /**
     * The expected input class for this async step.
     * Used for type validation and routing async results.
     * 
     * @return The input class type
     */
    Class<?> inputClass() default Map.class;
}