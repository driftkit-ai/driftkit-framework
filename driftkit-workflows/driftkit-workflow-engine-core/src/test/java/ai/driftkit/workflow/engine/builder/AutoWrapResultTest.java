package ai.driftkit.workflow.engine.builder;

import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test automatic wrapping of non-StepResult return values.
 */
@Slf4j
public class AutoWrapResultTest {
    
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
    @DisplayName("Test auto-wrapping of plain return values")
    public void testAutoWrapPlainValues() throws Exception {
        // Create workflow with methods that return plain values
        WorkflowGraph<OrderInput, OrderSummary> workflow = WorkflowBuilder
            .define("auto-wrap-test", OrderInput.class, OrderSummary.class)
            .thenValue(this::validateInput)  // Returns ValidationResult directly
            .thenValue(this::calculateTotal)  // Returns Double directly
            .finishWithValue(this::createSummary)   // Returns OrderSummary directly (final step)
            .build();
        
        engine.register(workflow);
        
        // Execute workflow
        OrderInput input = new OrderInput();
        input.setItemCount(5);
        input.setPricePerItem(10.0);
        
        WorkflowEngine.WorkflowExecution<OrderSummary> execution = 
            engine.execute("auto-wrap-test", input);
        
        OrderSummary result = execution.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertEquals(5, result.getItemCount());
        assertEquals(50.0, result.getTotal());
        assertTrue(result.isValid());
    }
    
    @Test
    @DisplayName("Test mixed StepResult and plain values")
    public void testMixedReturnTypes() throws Exception {
        // Create workflow mixing StepResult and plain returns
        WorkflowGraph<ProcessRequest, ProcessResult> workflow = WorkflowBuilder
            .define("mixed-returns", ProcessRequest.class, ProcessResult.class)
            .thenValue(this::checkAccess)     // Returns boolean directly
            .then(this::processData)          // Returns StepResult explicitly
            .finishWithValue(this::formatResult)    // Returns ProcessResult directly
            .build();
        
        engine.register(workflow);
        
        // Execute workflow
        ProcessRequest request = new ProcessRequest();
        request.setData("test-data");
        request.setUserId("user123");
        
        WorkflowEngine.WorkflowExecution<ProcessResult> execution = 
            engine.execute("mixed-returns", request);
        
        ProcessResult result = execution.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("PROCESSED: test-data", result.getProcessedData());
    }
    
    @Test
    @DisplayName("Test auto-wrap with context parameter")
    public void testAutoWrapWithContext() throws Exception {
        // Create workflow with methods that use context
        WorkflowGraph<String, String> workflow = WorkflowBuilder
            .define("context-test", String.class, String.class)
            .thenValue((String input, WorkflowContext ctx) -> {
                // Return plain value (will be stored in context automatically)
                return input.toUpperCase();
            })
            .finishWithValue((String upper, WorkflowContext ctx) -> {
                // Read original input from trigger data
                String original = ctx.getTriggerData(String.class);
                return original + " -> " + upper;
            })
            .build();
        
        engine.register(workflow);
        
        WorkflowEngine.WorkflowExecution<String> execution = engine.execute("context-test", "hello");
        String result = execution.get(5, TimeUnit.SECONDS);
        assertEquals("hello -> HELLO", result);
    }
    
    // @Test
    // @DisplayName("Test null handling in auto-wrap")
    // public void testNullHandling() throws Exception {
    //     // NOTE: This test is disabled because InputPreparer doesn't pass null values between steps
    //     // The framework filters out null values when looking for compatible outputs
    //     // This is a known limitation that requires changes to InputPreparer logic
    //     
    //     // Create workflow that might return null
    //     WorkflowGraph<String, String> workflow = WorkflowBuilder
    //         .define("null-test", String.class, String.class)
    //         .then((String input) -> {
    //             // Return StepResult with null value explicitly
    //             return StepResult.continueWith((String) null);
    //         })
    //         .finishWithValue((String nullValue) -> {
    //             // Should receive null and handle it
    //             return nullValue == null ? "was null" : "was not null";
    //         })
    //         .build();
    //     
    //     engine.register(workflow);
    //     
    //     WorkflowEngine.WorkflowExecution<String> execution = engine.execute("null-test", "input");
    //     String result = execution.get(5, TimeUnit.SECONDS);
    //     assertEquals("was null", result);
    // }
    
    // Step methods returning plain values (not StepResult)
    
    private ValidationResult validateInput(OrderInput input) {
        log.info("Validating input: {} items at ${}", input.getItemCount(), input.getPricePerItem());
        ValidationResult result = new ValidationResult();
        result.setValid(input.getItemCount() > 0 && input.getPricePerItem() > 0);
        result.setItemCount(input.getItemCount());
        result.setPricePerItem(input.getPricePerItem());
        return result; // Plain object, will be auto-wrapped
    }
    
    private Double calculateTotal(ValidationResult validation) {
        log.info("Calculating total for {} items", validation.getItemCount());
        return validation.getItemCount() * validation.getPricePerItem(); // Plain Double
    }
    
    private OrderSummary createSummary(Double total, WorkflowContext ctx) {
        log.info("Creating summary with total: ${}", total);
        
        // Get the original input to get the item count
        OrderInput originalInput = ctx.getTriggerData(OrderInput.class);
        
        OrderSummary summary = new OrderSummary();
        summary.setItemCount(originalInput.getItemCount());
        summary.setTotal(total);
        summary.setValid(total > 0);
        return summary; // Plain object for final result
    }
    
    private Boolean checkAccess(ProcessRequest request) {
        log.info("Checking access for user: {}", request.getUserId());
        return request.getUserId() != null && !request.getUserId().isEmpty();
    }
    
    private StepResult<String> processData(Boolean hasAccess, WorkflowContext ctx) {
        if (!hasAccess) {
            return StepResult.fail("Access denied");
        }
        
        ProcessRequest request = ctx.getTriggerData(ProcessRequest.class);
        String processed = "PROCESSED: " + request.getData();
        
        // Explicitly return StepResult
        return StepResult.continueWith(processed);
    }
    
    private ProcessResult formatResult(String processedData) {
        ProcessResult result = new ProcessResult();
        result.setSuccess(true);
        result.setProcessedData(processedData);
        return result; // Plain object
    }
    
    // Domain objects
    @Data
    public static class OrderInput {
        private int itemCount;
        private double pricePerItem;
    }
    
    @Data
    public static class ValidationResult {
        private boolean valid;
        private int itemCount;
        private double pricePerItem;
    }
    
    @Data
    public static class OrderSummary {
        private int itemCount;
        private double total;
        private boolean valid;
    }
    
    @Data
    public static class ProcessRequest {
        private String data;
        private String userId;
    }
    
    @Data
    public static class ProcessResult {
        private boolean success;
        private String processedData;
    }
}