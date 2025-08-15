package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance.WorkflowStatus;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for FluentAPI resume functionality after suspension.
 */
@Slf4j
public class FluentApiResumeTest {
    
    private WorkflowEngine engine;
    
    @BeforeEach
    public void setUp() {
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .progressTracker(new InMemoryProgressTracker())
            .chatSessionRepository(new InMemoryChatSessionRepository())
            .chatHistoryRepository(new InMemoryChatHistoryRepository())
            .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
            .suspensionDataRepository(new InMemorySuspensionDataRepository())
            .build();
        
        engine = new WorkflowEngine(config);
    }
    
    @Test
    @DisplayName("Test resume executes correct step after suspension")
    public void testResumeExecutesCorrectStep() throws Exception {
        // Add workflow listener to debug execution
        engine.addListener("test-listener", new WorkflowEngine.WorkflowExecutionListener() {
            @Override
            public void onStepCompleted(WorkflowInstance instance, String stepId, StepResult<?> result) {
                log.info("Step completed: {} with result type: {}", stepId, result.getClass().getSimpleName());
            }
            
            @Override
            public void onWorkflowCompleted(WorkflowInstance instance, Object result) {
                log.info("Workflow completed with result: {}", result);
            }
            
            @Override
            public void onWorkflowFailed(WorkflowInstance instance, Throwable error) {
                log.error("Workflow failed", error);
            }
        });
        // Create workflow with suspension point
        WorkflowGraph<OrderRequest, OrderResult> workflow = WorkflowBuilder
            .define("order-processing", OrderRequest.class, OrderResult.class)
            .then(this::validateOrder)
            .then(this::checkInventory)
            .then("step3", (InventoryResult inventory, WorkflowContext ctx) -> {
                if (!inventory.isAvailable()) {
                    return StepResult.suspend(
                        "Item not in stock. Continue with backorder?",
                        BackorderConfirmation.class
                    );
                }
                return StepResult.continueWith(inventory);
            })
            .then("step4", this::processPayment)
            .then("step5", this::createOrder)
            .build();
        
        engine.register(workflow);
        
        // Execute workflow with item not in stock
        OrderRequest request = new OrderRequest();
        request.setItemId("out-of-stock-item");
        request.setQuantity(5);
        
        WorkflowEngine.WorkflowExecution<OrderResult> execution = 
            engine.execute("order-processing", request);
        
        // Wait for suspension
        Thread.sleep(500);
        
        // Get workflow instance
        WorkflowInstance instance = engine.getWorkflowInstance(execution.getRunId()).orElse(null);
        assertNotNull(instance);
        assertEquals(WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // Log current state
        log.info("Suspended at step: {}", instance.getCurrentStepId());
        
        // Resume with confirmation
        BackorderConfirmation confirmation = new BackorderConfirmation();
        confirmation.setConfirmed(true);
        confirmation.setEstimatedDays(7);
        
        WorkflowEngine.WorkflowExecution<OrderResult> resumeExecution = 
            engine.resume(execution.getRunId(), confirmation);
        
        // Get result from resume execution, not original
        OrderResult result = resumeExecution.get(5, TimeUnit.SECONDS);
        
        // Verify result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("step5", result.getLastExecutedStep());
        assertTrue(result.isBackorder());
    }
    
    // Step methods
    private StepResult<ValidationResult> validateOrder(OrderRequest request) {
        log.info("Step 1: Validating order for item: {}", request.getItemId());
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        result.setItemId(request.getItemId());
        return StepResult.continueWith(result);
    }
    
    private StepResult<InventoryResult> checkInventory(ValidationResult validation, WorkflowContext ctx) {
        log.info("Step 2: Checking inventory for item: {}", validation.getItemId());
        InventoryResult result = new InventoryResult();
        result.setItemId(validation.getItemId());
        result.setAvailable(!"out-of-stock-item".equals(validation.getItemId()));
        return StepResult.continueWith(result);
    }
    
    private StepResult<PaymentResult> processPayment(Object input, WorkflowContext ctx) {
        log.info("Step 4: Processing payment");
        
        // Handle both InventoryResult and BackorderConfirmation
        boolean isBackorder = false;
        if (input instanceof BackorderConfirmation) {
            BackorderConfirmation confirmation = (BackorderConfirmation) input;
            isBackorder = confirmation.isConfirmed();
        }
        
        PaymentResult result = new PaymentResult();
        result.setProcessed(true);
        result.setBackorder(isBackorder);
        return StepResult.continueWith(result);
    }
    
    private StepResult<OrderResult> createOrder(PaymentResult payment, WorkflowContext ctx) {
        log.info("Step 5: Creating order");
        
        OrderResult result = new OrderResult();
        result.setSuccess(true);
        result.setLastExecutedStep("step5");
        result.setBackorder(payment.isBackorder());
        
        // Get backorder details from user input
        if (payment.isBackorder()) {
            // The user input (BackorderConfirmation) is available in context
            try {
                BackorderConfirmation confirmation = ctx.getStepResult(WorkflowContext.Keys.USER_INPUT, BackorderConfirmation.class);
                if (confirmation != null) {
                    result.setEstimatedDays(confirmation.getEstimatedDays());
                }
            } catch (Exception e) {
                // User input might not be available in this format
                log.debug("Could not get BackorderConfirmation from context", e);
            }
        }
        
        // Collect executed steps
        result.getExecutedSteps().add("step1");
        result.getExecutedSteps().add("step2");
        result.getExecutedSteps().add("step3");
        result.getExecutedSteps().add("step4");
        result.getExecutedSteps().add("step5");
        
        log.info("Finishing workflow with result: {}", result);
        return StepResult.finish(result);
    }
    
    // Domain objects
    @Data
    public static class OrderRequest {
        private String itemId;
        private int quantity;
    }
    
    @Data
    public static class OrderResult {
        private boolean success;
        private String lastExecutedStep;
        private boolean backorder;
        private int estimatedDays;
        private final java.util.Set<String> executedSteps = new java.util.HashSet<>();
    }
    
    @Data
    public static class ValidationResult {
        private boolean valid;
        private String itemId;
    }
    
    @Data
    public static class InventoryResult {
        private String itemId;
        private boolean available;
    }
    
    @Data
    public static class BackorderConfirmation {
        private boolean confirmed;
        private int estimatedDays;
    }
    
    @Data
    public static class PaymentResult {
        private boolean processed;
        private boolean backorder;
    }
}