package ai.driftkit.workflow.spring.integration;

import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatRequest;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatResponse;
import ai.driftkit.common.domain.Language;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import ai.driftkit.workflow.engine.schema.annotations.SchemaClass;
import ai.driftkit.workflow.engine.schema.SchemaProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import ai.driftkit.workflow.engine.spring.autoconfigure.WorkflowEngineAutoConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ChatWorkflow with Spring Boot.
 * Tests workflow registration, dependency injection, and execution through Spring context.
 */
@SpringBootTest(classes = {SpringChatWorkflowIntegrationTest.TestConfig.class})
@TestPropertySource(properties = {
    "driftkit.workflow.core-threads=2",
    "driftkit.workflow.max-threads=10",
    "driftkit.workflow.default-step-timeout-ms=5000"
})
@Slf4j
public class SpringChatWorkflowIntegrationTest {
    
    @Autowired
    private WorkflowService workflowService;
    
    @Autowired
    private WorkflowEngine workflowEngine;
    
    @Autowired
    private SpringChatWorkflow chatWorkflow;
    
    
    @Test
    public void testWorkflowRegistrationAndBasicExecution() throws Exception {
        // Test that workflow is automatically registered
        assertTrue(workflowEngine.getRegisteredWorkflows().contains("spring-chat-workflow"));
        
        // Start workflow with greeting
        Map<String, String> properties = new HashMap<>();
        properties.put("message", "Hello Spring!");
        ChatRequest request = new ChatRequest("test-chat-123", properties, Language.GENERAL, "spring-chat-workflow");
        request.setUserId("user-456");
        
        // Process through WorkflowService
        ChatResponse response = workflowService.processChatRequest(request);
        
        assertNotNull(response);
        assertEquals("test-chat-123", response.getChatId());
        assertTrue(response.isCompleted()); // Suspend returns completed response with nextSchema
        assertNotNull(response.getNextSchema()); // Should have schema for next input
        assertEquals("userChatMessage", response.getNextSchema().getSchemaName());
        
        Map<String, String> responseProps = response.getPropertiesMap();
        String message = responseProps.get("message");
        assertTrue(message != null && message.contains("Hello"));
    }
    
    @Test
    public void testAsyncTaskHandling() throws Exception {
        // Start workflow with a question
        Map<String, String> properties = new HashMap<>();
        properties.put("message", "What is dependency injection?");
        ChatRequest request = new ChatRequest("test-chat-async", properties, Language.GENERAL, "spring-chat-workflow");
        request.setUserId("user-789");
        
        // Execute workflow directly to get execution future
        var execution = workflowEngine.execute("spring-chat-workflow", request);
        String runId = execution.getRunId();
        
        // Wait for workflow to reach suspended state (after async completes)
        Thread.sleep(1000);
        
        // Check that workflow is suspended (waiting for user input)
        var instanceOpt = workflowEngine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        var instance = instanceOpt.get();
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // The workflow should be suspended waiting for UserChatMessage
        assertNotNull(instance.getSuspensionData());
        assertEquals(UserChatMessage.class, instance.getSuspensionData().nextInputClass());
        
        // Resume workflow with user message
        UserChatMessage userMessage = new UserChatMessage();
        userMessage.setMessage("Thanks for the explanation!");
        userMessage.setUserId("user-789");
        
        var resumeExecution = workflowEngine.resume(runId, userMessage);
        
        // Wait for completion
        Object finalResult = resumeExecution.get(2, TimeUnit.SECONDS);
        assertNotNull(finalResult);
        assertTrue(finalResult instanceof FeedbackResponse);
        
        FeedbackResponse feedbackResponse = (FeedbackResponse) finalResult;
        assertEquals("Thank you for your message. Conversation recorded by Spring service.", 
                    feedbackResponse.getMessage());
    }
    
    @Test
    public void testWorkflowResumption() throws Exception {
        // Start workflow
        Map<String, String> properties = new HashMap<>();
        properties.put("message", "Hello!");
        ChatRequest request = new ChatRequest("test-resume", properties, Language.GENERAL, "spring-chat-workflow");
        request.setUserId("user-999");
        
        // Execute workflow to get run ID
        var execution = workflowEngine.execute("spring-chat-workflow", request);
        String runId = execution.getRunId();
        
        // Wait for workflow to reach suspended state
        Thread.sleep(100);
        
        // Verify workflow is suspended
        var instanceOpt = workflowEngine.getWorkflowInstance(runId);
        assertTrue(instanceOpt.isPresent());
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instanceOpt.get().getStatus());
        
        UserChatMessage userMessage = new UserChatMessage();
        userMessage.setMessage("Thanks for the greeting!");
        userMessage.setUserId("user-999");
        
        // Resume the workflow
        var resumed = workflowEngine.resume(runId, userMessage);
        
        assertNotNull(resumed);
        assertEquals(runId, resumed.getRunId());
    }
    
    @Test
    public void testSpringDependencyInjection() {
        // Verify that Spring beans are properly injected
        assertNotNull(chatWorkflow.getSpringManagedService());
        assertEquals("Spring Service", chatWorkflow.getSpringManagedService().getServiceName());
    }
    
    /**
     * Spring configuration for the test
     */
    @Configuration
    @Import(WorkflowEngineAutoConfiguration.class)
    public static class TestConfig {
        
        @Bean
        public SpringManagedService springManagedService() {
            return new SpringManagedService();
        }
        
        @Bean
        public SpringChatWorkflow springChatWorkflow(SchemaProvider schemaProvider, SpringManagedService springManagedService) {
            return new SpringChatWorkflow(schemaProvider, springManagedService);
        }
    }
    
    /**
     * Chat workflow implementation with Spring integration
     */
    @Slf4j
    @Component
    @RequiredArgsConstructor
    @Workflow(
        id = "spring-chat-workflow",
        version = "1.0",
        description = "Chat workflow integrated with Spring"
    )
    public static class SpringChatWorkflow {
        
        private final SchemaProvider schemaProvider;
        private final SpringManagedService springManagedService;
        
        @InitialStep(description = "Process initial chat request")
        public StepResult<IntentAnalysis> processInitialRequest(
                WorkflowContext context, 
                ChatRequest request) {
            
            log.info("Processing request in Spring context: {}", request.getMessage());
            
            // Use Spring-managed service
            springManagedService.logRequest(request);
            
            // Store chat context
            ChatContextHelper.setChatId(context, request.getChatId());
            ChatContextHelper.setUserId(context, request.getUserId());
            ChatContextHelper.addUserMessage(context, request.getMessage());
            
            // Simple intent analysis
            IntentAnalysis analysis = new IntentAnalysis();
            String msg = request.getMessage();
            if (msg != null && msg.toLowerCase().contains("hello")) {
                analysis.setIntent(Intent.GREETING);
            } else {
                analysis.setIntent(Intent.QUESTION);
            }
            analysis.setConfidence(0.9);
            analysis.setQuery(msg != null ? msg : "");
            
            return new StepResult.Continue<>(analysis);
        }
        
        @Step(
            id = "routeByIntent",
            description = "Route based on intent",
            nextClasses = {GreetingEvent.class, QuestionEvent.class}
        )
        public StepResult<?> routeByIntent(
                WorkflowContext context,
                IntentAnalysis analysis) {
            
            if (analysis.getIntent() == Intent.GREETING) {
                return new StepResult.Branch<>(new GreetingEvent("Hello from Spring-integrated workflow!"));
            } else {
                return new StepResult.Branch<>(new QuestionEvent(
                    analysis.getQuery(), 
                    analysis.getConfidence()
                ));
            }
        }
        
        @Step(id = "handleGreeting", description = "Handle greeting", nextClasses = {UserChatMessage.class})
        public StepResult<SimpleChatResponse> handleGreeting(
                WorkflowContext context,
                GreetingEvent greeting) {
            
            // Create simple response
            SimpleChatResponse response = new SimpleChatResponse();
            response.setMessage("Hello! How can I help you today? [Processed by: " + 
                springManagedService.getServiceName() + "]");
            
            return StepResult.suspend(response, UserChatMessage.class);
        }
        
        @Step(id = "handleQuestion", description = "Handle question with async processing", nextClasses = {UserChatMessage.class})
        public StepResult<SimpleChatResponse> handleQuestion(
                WorkflowContext context,
                QuestionEvent question) {
            
            log.info("Handling question asynchronously: {}", question.query());
            
            // Simulate async search
            CompletableFuture<SimpleChatResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(500); // Simulate processing
                    SimpleChatResponse result = new SimpleChatResponse();
                    result.setMessage("Here's information about: " + question.query());
                    return result;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            
            // Create immediate response
            SimpleChatResponse immediateResponse = new SimpleChatResponse();
            immediateResponse.setMessage("Searching for information about: " + question.query() + "...");
            
            return new StepResult.Async(
                "question-search",
                500L,
                Map.of("query", question.query(), "future", future),
                immediateResponse
            );
        }
        
        @AsyncStep("question-search")
        public StepResult<SimpleChatResponse> processSearchResult(
                Map<String, Object> taskArgs,
                WorkflowContext context,
                AsyncProgressReporter progress) {
            
            // Get the future from task args
            @SuppressWarnings("unchecked")
            CompletableFuture<SimpleChatResponse> future = (CompletableFuture<SimpleChatResponse>) taskArgs.get("future");
            
            try {
                // Wait for the search result
                SimpleChatResponse searchResult = future.get();
                
                // Enhance the message
                searchResult.setMessage(searchResult.getMessage() + " [Enhanced by Spring context]");
                
                return StepResult.suspend(searchResult, UserChatMessage.class);
            } catch (Exception e) {
                return new StepResult.Fail<>(e);
            }
        }
        
        @Step(id = "processContinuation", description = "Process continued conversation")
        public StepResult<FeedbackResponse> processContinuation(
                WorkflowContext context,
                UserChatMessage userMessage) {
            
            log.info("User continued with: {}", userMessage.getMessage());
            
            FeedbackResponse response = new FeedbackResponse();
            response.setMessage("Thank you for your message. Conversation recorded by Spring service.");
            response.setRequestMoreFeedback(false);
            
            return new StepResult.Finish<>(response);
        }
        
        public SpringManagedService getSpringManagedService() {
            return springManagedService;
        }
    }
    
    /**
     * Simple Spring-managed service for testing dependency injection
     */
    @Component
    @Slf4j
    public static class SpringManagedService {
        
        public String getServiceName() {
            return "Spring Service";
        }
        
        public void logRequest(ChatRequest request) {
            log.info("Spring service processing request from user: {}", request.getUserId());
        }
    }
    
    // Simple domain objects for the test
    
    @Data
    public static class IntentAnalysis {
        private Intent intent;
        private double confidence;
        private String query;
        private Map<String, String> entities = new HashMap<>();
    }
    
    public enum Intent {
        GREETING, QUESTION, TASK_REQUEST, FEEDBACK, UNKNOWN
    }
    
    public record GreetingEvent(String message) {}
    public record QuestionEvent(String query, double confidence) {}
    
    @Data
    @SchemaClass(id = "simpleChatResponse", description = "Simple chat response")
    public static class SimpleChatResponse {
        @SchemaProperty(required = true, description = "Response message")
        private String message;
    }
    
    @Data
    public static class FeedbackResponse {
        private String message;
        private boolean requestMoreFeedback;
    }
    
    @Data
    @SchemaClass(id = "userChatMessage", description = "User chat message")
    public static class UserChatMessage {
        @SchemaProperty(required = true, description = "User's message")
        private String message;
        @SchemaProperty(description = "User ID")
        private String userId;
        @SchemaProperty(description = "Additional metadata")
        private Map<String, Object> metadata = new HashMap<>();
    }
}