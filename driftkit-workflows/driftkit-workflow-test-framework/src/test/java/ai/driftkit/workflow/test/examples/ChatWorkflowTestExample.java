package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.test.core.AnnotationWorkflowTest;
import ai.driftkit.workflow.test.utils.RetryTestUtils;
import ai.driftkit.workflow.test.mock.MockAIClient;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.Role;

import static ai.driftkit.workflow.test.assertions.WorkflowTestAssertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Example test demonstrating how to test a chat workflow using the test framework.
 */
public class ChatWorkflowTestExample extends AnnotationWorkflowTest {
    
    private MockAIClient mockAI;
    private TestChatWorkflow chatWorkflow;
    
    @BeforeEach
    void setUp() {
        // Parent class setup is handled automatically by @BeforeEach in base class
        
        mockAI = MockAIClient.builder()
            .defaultStrategy(MockAIClient.ResponseStrategy.fixed("Hello! How can I help you?"))
            .build();
        
        chatWorkflow = new TestChatWorkflow(mockAI);
    }
    
    @Override
    protected void registerWorkflows() {
        engine.register(chatWorkflow);
    }
    
    @Test
    void testSimpleChatFlow() throws Exception {
        // Arrange
        Map<String, String> properties = new HashMap<>();
        properties.put(ChatMessage.PROPERTY_MESSAGE, "Hello, I need help with my order");
        properties.put("userId", "user-123");
        ChatRequest request = new ChatRequest("test-chat-1", properties, null, "test-chat-workflow");
        
        // Configure mock AI response
        mockAI.whenPromptContains("order", "I can help you with your order. What's your order number?");
        
        // Act
        ChatResponse response = executeWorkflow("test-chat-workflow", request);
        
        // Assert
        assertNotNull(response);
        assertEquals("I can help you with your order. What's your order number?", response.message);
        assertEquals(1, mockAI.getCallCount());
        
        // Verify workflow state - using actual instance ID
        String instanceId = response.sessionId;
        assertNotNull(instanceId);
        WorkflowInstance instance = getWorkflowInstance(instanceId);
        assertNotNull(instance);
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
    }
    
    @Test
    void testChatWithRetry() throws Exception {
        // Arrange - Set up AI to fail first 2 times
        // Configure mock to fail first 2 times, then succeed
        testContext.configure(config -> config
            .mock().workflow("test-chat-workflow").step("processWithAI").times(2)
                .thenFail(new RuntimeException("Service unavailable"))
                .afterwards().thenSucceed("Success after retries!")
        );
        
        Map<String, String> properties = new HashMap<>();
        properties.put(ChatMessage.PROPERTY_MESSAGE, "Test message");
        ChatRequest request = new ChatRequest("test-chat-retry", properties, null, "test-chat-workflow");
        
        // Act
        ChatResponse response = executeWorkflow("test-chat-workflow", request);
        
        // Assert
        assertNotNull(response);
        assertEquals("Success after retries!", response.message);
        
        // Verify retries - we expect 3 total executions (1 initial + 2 retries)
        assertEquals(3, testInterceptor.getExecutionTracker().getExecutionCount("test-chat-workflow", "processWithAI"));
    }
    
    @Test
    void testChatBranching() throws Exception {
        // Arrange
        Map<String, String> urgentProperties = new HashMap<>();
        urgentProperties.put(ChatMessage.PROPERTY_MESSAGE, "URGENT: System is down!");
        urgentProperties.put("priority", "high");
        ChatRequest urgentRequest = new ChatRequest("test-urgent", urgentProperties, null, "test-chat-workflow");
        
        // Mock the branch decision using new API
        testContext.configure(config -> config
            .mock().workflow("test-chat-workflow").step("analyzeIntent")
                .when(ChatRequest.class, req -> req.getMessage().contains("URGENT"))
                .thenReturn(ChatRequest.class, req -> StepResult.branch(new UrgentEvent()))
        );
        
        testContext.configure(config -> config
            .mock().workflow("test-chat-workflow").step("analyzeIntent")
                .when(ChatRequest.class, req -> !req.getMessage().contains("URGENT"))
                .thenReturn(ChatRequest.class, req -> StepResult.branch(new NormalEvent()))
        );
        
        // Act
        ChatResponse response = executeWorkflow("test-chat-workflow", urgentRequest);
        
        // Assert
        assertEquals("Escalating to support team immediately!", response.message);
        
        // Verify execution path
        assertThat(testInterceptor.getExecutionTracker().getHistory())
            .containsStep("test-chat-workflow", "handleUrgent")
            .hasExecutionCount("test-chat-workflow", "handleNormal", 0);
    }
    
    @Test
    void testChatResume() throws Exception {
        // Arrange - First execution
        Map<String, String> initialProperties = new HashMap<>();
        initialProperties.put(ChatMessage.PROPERTY_MESSAGE, "I need help");
        ChatRequest initialRequest = new ChatRequest("test-resume", initialProperties, null, "test-chat-workflow");
        
        // Mock to suspend after initial message using new API
        testContext.configure(config -> config
            .mock().workflow("test-chat-workflow").step("processWithAI").always()
                .thenReturn(Object.class, input -> StepResult.suspend("How can I help you?", FollowUpMessage.class))
        );
        
        // Act - First execution
        WorkflowEngine.WorkflowExecution<ChatResponse> execution = executeWorkflowAsync("test-chat-workflow", initialRequest);
        
        // Wait for suspension
        waitForStatus(execution.getRunId(), WorkflowInstance.WorkflowStatus.SUSPENDED);
        
        // Verify suspended
        WorkflowInstance instance = getWorkflowInstance(execution.getRunId());
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // Arrange - Resume with follow-up
        FollowUpMessage followUp = new FollowUpMessage();
        followUp.message = "My order number is 12345";
        
        mockAI.whenPromptContains("12345", "Found your order! It will arrive tomorrow.");
        
        // Act - Resume workflow
        ChatResponse secondResponse = resumeWorkflow(execution.getRunId(), followUp);
        
        // Assert
        assertEquals("Found your order! It will arrive tomorrow.", secondResponse.message);
        
        // Verify workflow completed
        instance = getWorkflowInstance(execution.getRunId());
        assertEquals(WorkflowInstance.WorkflowStatus.COMPLETED, instance.getStatus());
    }
    
    // Test workflow implementation
    @Workflow(id = "test-chat-workflow", version = "1.0")
    private static class TestChatWorkflow {
        private final ModelClient aiClient;
        
        public TestChatWorkflow(ModelClient aiClient) {
            this.aiClient = aiClient;
        }
        
        @InitialStep
        public StepResult<IntentAnalysis> analyzeIntent(WorkflowContext context, ChatRequest request) {
            // Simple intent analysis
            IntentAnalysis analysis = new IntentAnalysis();
            if (request.getMessage().contains("URGENT")) {
                analysis.setIntent("urgent");
            } else {
                analysis.setIntent("normal");
            }
            return StepResult.continueWith(analysis);
        }
        
        @Step(id = "processWithAI", retryPolicy = @RetryPolicy(maxAttempts = 3, delay = 100))
        public StepResult<String> processWithAI(WorkflowContext context, ChatRequest request) {
            // Call AI
            var response = aiClient.textToText(ModelTextRequest.builder()
                .messages(List.of(new ModelImageResponse.ModelContentMessage(Role.user, request.getMessage(), null)))
                .build());
            
            return StepResult.continueWith(response.getResponse());
        }
        
        @Step(id = "handleUrgent")
        public StepResult<ChatResponse> handleUrgent(WorkflowContext context, UrgentEvent event) {
            ChatResponse response = new ChatResponse();
            response.message = "Escalating to support team immediately!";
            response.sessionId = context.getInstanceId();
            return StepResult.finish(response);
        }
        
        @Step(id = "handleNormal")
        public StepResult<ChatResponse> handleNormal(WorkflowContext context, NormalEvent event) {
            // Get AI response from context
            String aiResponse = context.getStepResult("processWithAI", String.class);
            
            ChatResponse response = new ChatResponse();
            response.message = aiResponse;
            response.sessionId = context.getInstanceId();
            return StepResult.suspend(response, FollowUpMessage.class);
        }
        
        @Step(id = "processFollowUp")
        public StepResult<ChatResponse> processFollowUp(WorkflowContext context, FollowUpMessage followUp) {
            // Process follow-up with AI
            var aiResponse = aiClient.textToText(ModelTextRequest.builder()
                .messages(List.of(new ModelImageResponse.ModelContentMessage(Role.user, followUp.message, null)))
                .build());
            
            ChatResponse response = new ChatResponse();
            response.message = aiResponse.getResponse();
            response.sessionId = context.getInstanceId();
            return StepResult.finish(response);
        }
    }
    
    // Domain classes
    @Data
    static class ChatResponse {
        String message;
        String sessionId;
    }
    
    @Data
    static class IntentAnalysis {
        private String intent;
    }
    
    static class UrgentEvent {}
    static class NormalEvent {}
    
    @Data
    static class FollowUpMessage {
        String message;
    }
}