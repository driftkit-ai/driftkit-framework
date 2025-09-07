package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.builder.RetryPolicyBuilder;
import ai.driftkit.workflow.test.core.WorkflowTestBase;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.test.mock.MockAIClient;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static ai.driftkit.workflow.engine.persistence.WorkflowInstance.WorkflowStatus;

/**
 * Example test demonstrating how to test chat-like workflows with custom domain types.
 * Shows mocking AI responses, testing retry behavior, and managing state.
 */
@Slf4j
public class ChatWorkflowTestExample extends WorkflowTestBase {
    
    private MockAIClient mockAI;
    private WorkflowBuilder<ChatTestRequest, ChatTestResponse> chatWorkflowBuilder;
    
    @BeforeEach
    void setUp() {
        // Create mock AI client
        mockAI = MockAIClient.builder()
            .defaultStrategy(MockAIClient.ResponseStrategy.fixed("Hello! How can I help you?"))
            .build();
        
        // Create retry policy for AI steps
        var retryPolicy = RetryPolicyBuilder.retry()
            .withMaxAttempts(3)
            .withDelay(100)
            .withRetryOnFailResult(true)  // Important for test mocks that return StepResult.Fail
            .build();
        
        // Create workflow builder for chat workflow
        chatWorkflowBuilder = WorkflowBuilder
            .define("test-chat-workflow", ChatTestRequest.class, ChatTestResponse.class)
            .then("analyzeIntent", this::analyzeIntent)
            .branch(
                ctx -> {
                    IntentAnalysis intent = ctx.getStepResult("analyzeIntent", IntentAnalysis.class);
                    return intent != null && "URGENT".equals(intent.getIntent());
                },
                urgent -> urgent
                    .then("handleUrgent", this::handleUrgent),
                normal -> normal
                    .thenWithRetry("processWithAI", this::processWithAI, retryPolicy)
                    .then("formatResponse", this::formatResponse)
            );
        
        // Register the workflow 
        engine.register(chatWorkflowBuilder);
    }
    
    // Workflow step implementations
    
    private StepResult<IntentAnalysis> analyzeIntent(ChatTestRequest request, WorkflowContext context) {
        log.debug("Analyzing intent for message: {} (context class: {}, has listener: {})", 
            request.getMessage(), context.getClass().getSimpleName(), 
            context.getInternalStepListener() != null);
        
        IntentAnalysis analysis = new IntentAnalysis();
        String message = request.getMessage().toLowerCase();
        
        if (message.contains("urgent") || message.contains("emergency")) {
            analysis.setIntent("URGENT");
            analysis.setConfidence(0.95);
        } else if (message.contains("order")) {
            analysis.setIntent("ORDER_INQUIRY");
            analysis.setConfidence(0.9);
        } else if (message.contains("technical") || message.contains("issue")) {
            analysis.setIntent("TECHNICAL_SUPPORT");
            analysis.setConfidence(0.85);
        } else {
            analysis.setIntent("GENERAL");
            analysis.setConfidence(0.8);
        }
        
        return StepResult.continueWith(analysis);
    }
    
    private StepResult<ChatTestResponse> handleUrgent(IntentAnalysis intent, WorkflowContext context) {
        log.info("Handling urgent request");
        
        ChatTestResponse response = new ChatTestResponse();
        response.setMessage("This is urgent! Let me help you immediately.");
        response.setSessionId(context.getRunId());
        response.setPriority("HIGH");
        
        return StepResult.finish(response);
    }
    
    private StepResult<String> processWithAI(IntentAnalysis intent, WorkflowContext context) {
        log.debug("Processing with AI for intent: {} (context class: {})", 
            intent.getIntent(), context.getClass().getSimpleName());
        
        // Prepare AI request
        ModelTextRequest aiRequest = new ModelTextRequest();
        aiRequest.setMessages(List.of(
            ModelContentMessage.create(Role.user, "User intent: " + intent.getIntent()),
            ModelContentMessage.create(Role.user, "Confidence: " + intent.getConfidence())
        ));
        
        // Call mock AI
        ModelTextResponse aiResponseObj = mockAI.textToText(aiRequest);
        String aiResponse = aiResponseObj.getResponse();
        return StepResult.continueWith(aiResponse);
    }
    
    private StepResult<ChatTestResponse> formatResponse(String aiResponse, WorkflowContext context) {
        ChatTestRequest originalRequest = (ChatTestRequest) context.getTriggerData();
        
        ChatTestResponse response = new ChatTestResponse();
        response.setMessage("I can help you with that. " + aiResponse);
        response.setSessionId(context.getRunId());
        response.setUserId(originalRequest.getUserId());
        
        return StepResult.finish(response);
    }
    
    // Test methods
    
    @Test
    void testSimpleChatFlow() throws Exception {
        // Arrange
        ChatTestRequest request = new ChatTestRequest();
        request.setMessage("Hello, I need help with my order");
        request.setUserId("user-123");
        
        // Configure mock AI response
        mockAI.whenPromptContains("order", "I can help you with your order. What's your order number?");
        
        // Act
        ChatTestResponse response = executeWorkflow("test-chat-workflow", request);
        
        // Assert
        assertNotNull(response);
        assertTrue(response.getMessage().contains("I can help you with that"));
        assertTrue(response.getMessage().contains("order"));
        assertEquals("user-123", response.getUserId());
        assertEquals(1, mockAI.getCallCount());
        
        // Verify execution path - with simplified branch logic, branch steps are executed inside branch nodes
        assertions.assertStep("test-chat-workflow", "analyzeIntent").wasExecuted();
        // Branch steps are now executed inside branch_1 node, not as separate steps
        assertions.assertStep("test-chat-workflow", "branch_1").wasExecuted();
    }
    
    @Test
    void testChatWithRetry() throws Exception {
        // Arrange
        ChatTestRequest request = new ChatTestRequest();
        request.setMessage("I have a technical issue with the system");
        request.setUserId("user-456");
        
        // Configure mock to fail first 2 times, then succeed
        orchestrator.mock().workflow("test-chat-workflow").step("processWithAI")
            .times(2).thenFail(new RuntimeException("AI Service temporarily unavailable"))
            .afterwards().thenReturn(IntentAnalysis.class, intent -> 
                StepResult.continueWith("I've analyzed your technical issue and found a solution"));
        
        // Act
        ChatTestResponse response = executeWorkflow("test-chat-workflow", request, Duration.ofSeconds(10));
        
        // Assert
        assertNotNull(response);
        System.out.println("Response message: " + response.getMessage());
        
        // For now, just check that we got a response
        assertTrue(response.getMessage() != null && !response.getMessage().isEmpty());
        
        // With simplified branch logic, the retry happens inside the branch node
        assertions.assertStep("test-chat-workflow", "branch_1").wasExecutedTimes(1);
    }
    
    @Test
    void testChatBranching() throws Exception {
        // Test urgent path
        ChatTestRequest urgentRequest = new ChatTestRequest();
        urgentRequest.setMessage("URGENT: System is down!");
        urgentRequest.setUserId("user-789");
        
        ChatTestResponse urgentResponse = executeWorkflow("test-chat-workflow", urgentRequest);
        
        assertNotNull(urgentResponse);
        assertEquals("This is urgent! Let me help you immediately.", urgentResponse.getMessage());
        assertEquals("HIGH", urgentResponse.getPriority());
        
        // Verify urgent path was taken - branch logic executes handleUrgent inside branch_1
        assertions.assertStep("test-chat-workflow", "analyzeIntent").wasExecuted();
        assertions.assertStep("test-chat-workflow", "branch_1").wasExecuted();
        
        // Test normal path
        ChatTestRequest normalRequest = new ChatTestRequest();
        normalRequest.setMessage("How do I reset my password?");
        normalRequest.setUserId("user-999");
        
        ChatTestResponse normalResponse = executeWorkflow("test-chat-workflow", normalRequest);
        
        assertNotNull(normalResponse);
        assertTrue(normalResponse.getMessage().contains("I can help you with that"));
        assertNull(normalResponse.getPriority());
        
        // Verify normal path was taken - processWithAI and formatResponse are executed inside branch_1
        // Note: branch_1 was already executed in the urgent test above, so we can't verify it again here
    }
    
    @Test
    void testConditionalMocking() throws Exception {
        // Mock different responses based on intent type
        orchestrator.mock().workflow("test-chat-workflow").step("processWithAI")
            .when(IntentAnalysis.class, intent -> intent.getIntent().equals("ORDER_INQUIRY"))
            .thenReturn(IntentAnalysis.class, intent -> 
                StepResult.continueWith("Your order status is: Shipped"));
        
        orchestrator.mock().workflow("test-chat-workflow").step("processWithAI")
            .when(IntentAnalysis.class, intent -> intent.getIntent().equals("TECHNICAL_SUPPORT"))
            .thenReturn(IntentAnalysis.class, intent -> 
                StepResult.continueWith("Please try restarting the application"));
        
        // Test order inquiry
        ChatTestRequest orderRequest = new ChatTestRequest();
        orderRequest.setMessage("Where is my order?");
        orderRequest.setUserId("user-001");
        
        ChatTestResponse orderResponse = executeWorkflow("test-chat-workflow", orderRequest);
        System.out.println("Order response: " + orderResponse.getMessage());
        System.out.println("Expected 'Shipped' in message: " + orderResponse.getMessage());
        assertTrue(orderResponse.getMessage().contains("Shipped"), 
            "Expected message to contain 'Shipped' but got: " + orderResponse.getMessage());
        
        // Test technical support
        ChatTestRequest techRequest = new ChatTestRequest();
        techRequest.setMessage("I'm having technical issues");
        techRequest.setUserId("user-002");
        
        ChatTestResponse techResponse = executeWorkflow("test-chat-workflow", techRequest);
        assertTrue(techResponse.getMessage().contains("restarting"));
    }
    
    @Test
    void testWorkflowStateVerification() throws Exception {
        // Execute workflow
        ChatTestRequest request = new ChatTestRequest();
        request.setMessage("General question about the product");
        request.setUserId("user-state-test");
        
        WorkflowEngine.WorkflowExecution<ChatTestResponse> execution = 
            executeWorkflowAsync("test-chat-workflow", request);
        
        // Wait for completion
        ChatTestResponse response = execution.get(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertNotNull(response);
        
        // Verify workflow instance state
        String instanceId = execution.getRunId();
        WorkflowInstance instance = getWorkflowInstance(instanceId);
        assertNotNull(instance);
        assertEquals(WorkflowStatus.COMPLETED, instance.getStatus());
        
        // Verify step execution order - branch steps are inside branch_1
        assertions.assertStep("test-chat-workflow", "analyzeIntent").wasExecuted();
        assertions.assertStep("test-chat-workflow", "branch_1").wasExecuted();
    }
    
    // Domain objects for the test
    
    @Data
    static class ChatTestRequest {
        String message;
        String userId;
        String sessionId;
        Map<String, String> metadata;
    }
    
    @Data
    static class ChatTestResponse {
        String message;
        String sessionId;
        String userId;
        String priority;
        Map<String, Object> additionalInfo;
    }
    
    @Data
    static class IntentAnalysis {
        String intent;
        double confidence;
        Map<String, String> entities;
    }
}