package ai.driftkit.workflow.test.examples;

import ai.driftkit.workflow.engine.annotations.*;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import ai.driftkit.workflow.engine.schema.SchemaName;
import ai.driftkit.workflow.engine.schema.SchemaDescription;
import ai.driftkit.workflow.engine.schema.SchemaProperty;
import ai.driftkit.workflow.test.core.AnnotationWorkflowTest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test demonstrating workflows where the initial step only takes WorkflowContext
 * and all data is initialized through the context (e.g., using ChatContextHelper).
 */
@Slf4j
@DisplayName("Context-Only Workflow Tests")
public class ContextOnlyWorkflowTest extends AnnotationWorkflowTest {
    
    private ContextBasedWorkflow workflow;
    
    @BeforeEach
    void setUp() {
        workflow = new ContextBasedWorkflow();
        registerWorkflow(workflow);
    }
    
    @Test
    @DisplayName("Should execute workflow with context-only initial step using null trigger data")
    void testWorkflowWithNullTriggerData() throws Exception {
        // Execute workflow with null trigger data - this should now work because initial step only uses context
        var execution = executeAndExpectSuspend("context-based-workflow", null, Duration.ofSeconds(5));
        String runId = execution.getRunId();
        
        // Verify the workflow suspended with the welcome message
        var instance = getWorkflowInstance(runId);
        assertNotNull(instance);
        assertEquals(WorkflowInstance.WorkflowStatus.SUSPENDED, instance.getStatus());
        
        // Get suspension data
        var context = instance.getContext();
        assertNotNull(context);
        var lastStepId = context.getLastStepId();
        assertNotNull(lastStepId);
        
        var suspensionData = context.getStepResult(lastStepId, WelcomeMessage.class);
        assertNotNull(suspensionData);
        assertEquals("Welcome! Please tell me your name.", suspensionData.getMessage());
        assertNull(suspensionData.getUserId()); // No userId because context wasn't initialized
        
        // Resume with user data
        UserData userData = new UserData();
        userData.setName("John Doe");
        
        var result = resumeWorkflow(runId, userData);
        assertNotNull(result);
        assertTrue(result instanceof Greeting);
        
        Greeting greeting = (Greeting) result;
        assertEquals("Hello, John Doe! Nice to meet you.", greeting.getGreeting());
    }
    
    @Test
    @DisplayName("Should execute workflow with ChatContextHelper initialization")
    void testWorkflowWithChatContextHelper() throws Exception {
        // Initialize context with ChatContextHelper
        WorkflowContext initialContext = ChatContextHelper.initChatContext("chat-123", "user-456", null);
        
        // Execute workflow with initialized context
        var execution = executeAndExpectSuspend("context-based-workflow", initialContext, Duration.ofSeconds(5));
        String runId = execution.getRunId();
        
        // Verify the workflow suspended with the welcome message including userId
        var instance = getWorkflowInstance(runId);
        assertNotNull(instance);
        
        var context = instance.getContext();
        var lastStepId = context.getLastStepId();
        var suspensionData = context.getStepResult(lastStepId, WelcomeMessage.class);
        
        assertNotNull(suspensionData);
        assertEquals("Welcome! Please tell me your name.", suspensionData.getMessage());
        assertEquals("user-456", suspensionData.getUserId()); // userId from context
        
        // Resume with user data
        UserData userData = new UserData();
        userData.setName("Jane Smith");
        
        var result = resumeWorkflow(runId, userData);
        assertNotNull(result);
        assertTrue(result instanceof Greeting);
        
        Greeting greeting = (Greeting) result;
        assertEquals("Hello, Jane Smith! Nice to meet you.", greeting.getGreeting());
        assertEquals("user-456", greeting.getUserId());
    }
    
    @Test
    @DisplayName("Should verify step execution for context-only workflow")
    void testStepExecution() throws Exception {
        WorkflowContext initialContext = ChatContextHelper.initChatContext("chat-789", "user-789", null);
        
        var execution = executeAndExpectSuspend("context-based-workflow", initialContext);
        String runId = execution.getRunId();
        
        // Verify initial step was executed
        assertions.assertStep("context-based-workflow", "welcomeUser").wasExecuted();
        
        // Resume with user data
        UserData userData = new UserData();
        userData.setName("Test User");
        
        resumeWorkflow(runId, userData);
        
        // Verify greeting step was executed
        assertions.assertStep("context-based-workflow", "greetUser").wasExecuted();
        
        // Verify both steps were executed
        assertions.assertStep("context-based-workflow", "welcomeUser").wasExecutedTimes(1);
        assertions.assertStep("context-based-workflow", "greetUser").wasExecutedTimes(1);
    }
    
    /**
     * Test workflow where initial step only takes WorkflowContext
     */
    @Workflow(
        id = "context-based-workflow",
        version = "1.0",
        description = "Workflow that starts with only context"
    )
    public static class ContextBasedWorkflow {
        
        @InitialStep
        @Step(nextSteps = {"greetUser"})
        public StepResult<WelcomeMessage> welcomeUser(WorkflowContext context) {
            log.info("Starting workflow with context: {}", context.getRunId());
            
            // Get userId from context if available (e.g., set by ChatContextHelper)
            String userId = ChatContextHelper.getUserId(context);
            
            WelcomeMessage welcome = new WelcomeMessage();
            welcome.setMessage("Welcome! Please tell me your name.");
            welcome.setUserId(userId);
            
            // Store some initial data in context
            context.setStepOutput("welcomeTime", System.currentTimeMillis());
            
            return StepResult.suspend(welcome, UserData.class);
        }
        
        @Step(id = "greetUser")
        public StepResult<Greeting> greetUser(WorkflowContext context, UserData userData) {
            log.info("Greeting user: {}", userData.getName());
            
            // Retrieve data from context
            Long welcomeTime = context.getStepResultOrDefault("welcomeTime", Long.class, 0L);
            String userId = ChatContextHelper.getUserId(context);
            
            Greeting greeting = new Greeting();
            greeting.setGreeting("Hello, " + userData.getName() + "! Nice to meet you.");
            greeting.setUserId(userId);
            greeting.setProcessingTime(System.currentTimeMillis() - welcomeTime);
            
            return StepResult.finish(greeting);
        }
    }
    
    // Domain classes
    
    @Data
    @SchemaName("WelcomeMessage")
    @SchemaDescription("Initial welcome message")
    public static class WelcomeMessage {
        @SchemaProperty(description = "Welcome message text", required = true)
        private String message;
        
        @SchemaProperty(description = "User ID from context if available")
        private String userId;
    }
    
    @Data
    @SchemaName("UserData")
    @SchemaDescription("User information")
    public static class UserData {
        @SchemaProperty(description = "User's name", required = true)
        private String name;
    }
    
    @Data
    @SchemaName("Greeting")
    @SchemaDescription("Final greeting response")
    public static class Greeting {
        @SchemaProperty(description = "Greeting message", required = true)
        private String greeting;
        
        @SchemaProperty(description = "User ID if available")
        private String userId;
        
        @SchemaProperty(description = "Processing time in milliseconds")
        private Long processingTime;
    }
}