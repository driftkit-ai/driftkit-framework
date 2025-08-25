package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.builder.*;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.test.core.FluentWorkflowTest;
import ai.driftkit.workflow.test.utils.RetryTestUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.driftkit.workflow.test.assertions.WorkflowTestAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Example test demonstrating how to test fluent API workflows.
 */
@Slf4j
public class FluentWorkflowTestExample extends FluentWorkflowTest {
    
    private WorkflowBuilder<OrderRequest, OrderResult> orderWorkflow;
    
    @BeforeEach
    void setUp() {
        // Parent class setup handled automatically
        
        // Create production workflow using fluent API
        orderWorkflow = WorkflowBuilder
            .define("order-processing", OrderRequest.class, OrderResult.class)
            .then("validate", this::validateOrder)
                .withRetryPolicy(
                    RetryPolicyBuilder.retry()
                        .withMaxAttempts(3)
                        .withDelay(100)
                        .build()
                )
            .branch(
                (WorkflowContext ctx) -> {
                    OrderRequest order = ctx.getStepResult("validate", OrderRequest.class);
                    return order != null && order.getAmount() > 1000;
                },
                
                // High value orders
                highValue -> highValue
                    .then("fraud-check", this::checkFraud)
                    .then("approve-high-value", this::approveHighValue),
                
                // Normal orders
                normal -> normal
                    .then("process-normal", this::processNormalOrder)
            );
    }
    
    @Override
    protected void registerWorkflows() {
        registerWorkflow(orderWorkflow);
    }
    
    @Test
    void testNormalOrderFlow() throws Exception {
        // Arrange
        OrderRequest order = new OrderRequest();
        order.setOrderId("ORD-123");
        order.setAmount(500); // Normal order
        order.setCustomerId("CUST-456");
        
        // Act
        OrderResult result = executeWorkflow("order-processing", order);
        
        // Assert
        assertNotNull(result);
        assertEquals("ORD-123", result.getOrderId());
        assertEquals("APPROVED", result.getStatus());
        
        // Verify execution path
        assertThat(testInterceptor.getExecutionTracker().getHistory())
            .containsStep("order-processing", "validate")
            .containsStep("order-processing", "process-normal")
            .hasExecutionCount("order-processing", "fraud-check", 0); // High value path not taken
    }
    
    @Test
    void testHighValueOrderWithFraudCheck() throws Exception {
        // Arrange
        OrderRequest order = new OrderRequest();
        order.setOrderId("ORD-789");
        order.setAmount(5000); // High value order
        order.setCustomerId("CUST-999");
        
        // Mock fraud check to pass
        testContext.configure(config -> config.mock().workflow("order-processing").step("fraud-check").always()
            .thenReturn(OrderRequest.class, req -> {
                FraudCheckResult result = new FraudCheckResult();
                result.setPassed(true);
                result.setRiskScore(0.2);
                return StepResult.continueWith(result);
            })
        );
        
        // Act
        OrderResult result = executeWorkflow("order-processing", order);
        
        // Assert
        assertEquals("APPROVED", result.getStatus());
        assertTrue(result.getNotes().contains("High value order approved"));
        
        // Verify high value path was taken
        assertThat(testInterceptor.getExecutionTracker().getHistory())
            .executedInOrder("validate", "fraud-check", "approve-high-value");
    }
    
    @Test
    void testOrderValidationWithRetry() throws Exception {
        // Arrange
        OrderRequest order = new OrderRequest();
        order.setOrderId("ORD-RETRY");
        order.setAmount(750);
        order.setCustomerId("CUST-RETRY");
        
        // Set up validation to fail twice then succeed
        AtomicInteger attempts = new AtomicInteger(0);
        testContext.configure(config -> config.mock().workflow("order-processing").step("validate").times(2)
            .thenFail(new RuntimeException("Validation service unavailable"))
            .afterwards().thenReturn(OrderRequest.class, req -> StepResult.continueWith(req))
        );
        
        // Act
        OrderResult result = executeWorkflow("order-processing", order);
        
        // Assert
        assertEquals("APPROVED", result.getStatus());
        
        // Verify retries - should be 3 total attempts
        assertEquals(3, testInterceptor.getExecutionTracker().getExecutionCount("order-processing", "validate"));
    }
    
    @Test
    void testFraudCheckFailure() throws Exception {
        // Arrange
        OrderRequest order = new OrderRequest();
        order.setOrderId("ORD-FRAUD");
        order.setAmount(10000); // High value
        order.setCustomerId("CUST-FRAUD");
        
        // Mock fraud check to fail
        testContext.configure(config -> config.mock().workflow("order-processing").step("fraud-check").always()
            .thenReturn(OrderRequest.class, req -> {
                FraudCheckResult result = new FraudCheckResult();
                result.setPassed(false);
                result.setRiskScore(0.9);
                result.setReason("Suspicious activity detected");
                return StepResult.continueWith(result);
            })
        );
        
        // Mock high value approval to reject based on fraud check
        testContext.configure(config -> config.mock().workflow("order-processing").step("approve-high-value").always()
            .thenReturn(FraudCheckResult.class, fraud -> {
                OrderResult result = new OrderResult();
                result.setOrderId("ORD-FRAUD");
                result.setStatus("REJECTED");
                result.setNotes("Failed fraud check: " + fraud.getReason());
                return StepResult.finish(result);
            })
        );
        
        // Act
        OrderResult result = executeWorkflow("order-processing", order);
        
        // Assert
        assertEquals("REJECTED", result.getStatus());
        assertTrue(result.getNotes().contains("Suspicious activity"));
    }
    
    @Test
    void testConditionalMocking() throws Exception {
        // Arrange - Mock different responses based on customer ID
        testContext.configure(config -> config.mock().workflow("order-processing").step("process-normal")
            .when(OrderRequest.class, req -> req.getCustomerId().startsWith("VIP"))
            .thenReturn(OrderRequest.class, req -> {
                OrderResult result = new OrderResult();
                result.setOrderId(req.getOrderId());
                result.setStatus("APPROVED");
                result.setNotes("VIP customer - expedited processing");
                return StepResult.finish(result);
            })
        );
        
        // Test VIP customer
        OrderRequest vipOrder = new OrderRequest();
        vipOrder.setOrderId("ORD-VIP");
        vipOrder.setCustomerId("VIP-001");
        vipOrder.setAmount(800);
        
        OrderResult vipResult = executeWorkflow("order-processing", vipOrder);
        assertTrue(vipResult.getNotes().contains("VIP customer"));
        
        // Test normal customer
        OrderRequest normalOrder = new OrderRequest();
        normalOrder.setOrderId("ORD-NORMAL");
        normalOrder.setCustomerId("CUST-001");
        normalOrder.setAmount(800);
        
        OrderResult normalResult = executeWorkflow("order-processing", normalOrder);
        assertFalse(normalResult.getNotes().contains("VIP"));
    }
    
    // Production workflow step implementations
    
    private StepResult<OrderRequest> validateOrder(OrderRequest order, WorkflowContext context) {
        log.info("Validating order: {}", order.getOrderId());
        
        if (order.getAmount() <= 0) {
            return StepResult.fail("Invalid order amount");
        }
        
        if (order.getCustomerId() == null) {
            return StepResult.fail("Customer ID required");
        }
        
        return StepResult.continueWith(order);
    }
    
    private StepResult<FraudCheckResult> checkFraud(OrderRequest order, WorkflowContext context) {
        log.info("Checking fraud for high value order: {}", order.getOrderId());
        
        // In production, this would call external fraud service
        FraudCheckResult result = new FraudCheckResult();
        result.setPassed(true);
        result.setRiskScore(0.1);
        
        return StepResult.continueWith(result);
    }
    
    private StepResult<OrderResult> processNormalOrder(OrderRequest order, WorkflowContext context) {
        log.info("Processing normal order: {}", order.getOrderId());
        
        OrderResult result = new OrderResult();
        result.setOrderId(order.getOrderId());
        result.setStatus("APPROVED");
        result.setNotes("Normal order processed");
        
        return StepResult.finish(result);
    }
    
    private StepResult<OrderResult> approveHighValue(FraudCheckResult fraudCheck, WorkflowContext context) {
        log.info("Approving high value order with fraud score: {}", fraudCheck.getRiskScore());
        
        OrderResult result = new OrderResult();
        result.setOrderId("HIGH-VALUE");
        
        if (fraudCheck.isPassed()) {
            result.setStatus("APPROVED");
            result.setNotes("High value order approved after fraud check");
        } else {
            result.setStatus("REJECTED");
            result.setNotes("High value order rejected: " + fraudCheck.getReason());
        }
        
        return StepResult.finish(result);
    }
    
    // Domain classes
    
    @Data
    static class OrderRequest {
        String orderId;
        String customerId;
        double amount;
    }
    
    @Data
    static class OrderResult {
        String orderId;
        String status;
        String notes;
    }
    
    @Data
    static class FraudCheckResult {
        boolean passed;
        double riskScore;
        String reason;
    }
}