package ai.driftkit.workflow.engine.integration;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.common.service.impl.InMemoryChatStore;
import ai.driftkit.common.service.impl.SimpleTextTokenizer;
import ai.driftkit.workflow.engine.builder.StepDefinition;
import ai.driftkit.workflow.engine.builder.WorkflowBuilder;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.chat.ChatResponseExtensions;
import ai.driftkit.workflow.engine.core.*;
import ai.driftkit.workflow.engine.domain.WorkflowEngineConfig;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.inmemory.*;
import ai.driftkit.workflow.engine.async.InMemoryProgressTracker;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaName;
import ai.driftkit.workflow.engine.schema.SchemaSystem;
import ai.driftkit.workflow.engine.schema.SchemaUtils;
import ai.driftkit.workflow.engine.schema.SchemaDescription;
import ai.driftkit.workflow.engine.service.DefaultWorkflowExecutionService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for @SchemaSystem annotation functionality
 */
@Slf4j
public class SystemSchemaIntegrationTest {

    private WorkflowEngine engine;
    private DefaultWorkflowExecutionService workflowService;

    @BeforeEach
    public void setUp() {
        // Initialize test configuration
        WorkflowEngineConfig config = WorkflowEngineConfig.builder()
            .stateRepository(new InMemoryWorkflowStateRepository())
            .progressTracker(new InMemoryProgressTracker())
            .chatSessionRepository(new InMemoryChatSessionRepository())
            .chatStore(new InMemoryChatStore(new SimpleTextTokenizer()))
            .asyncStepStateRepository(new InMemoryAsyncStepStateRepository())
            .suspensionDataRepository(new InMemorySuspensionDataRepository())
            .coreThreads(2)
            .maxThreads(4)
            .defaultStepTimeoutMs(5000)
            .build();
        
        engine = new WorkflowEngine(config);
        
        // Initialize workflow service
        workflowService = new DefaultWorkflowExecutionService(
            engine,
            config.getChatSessionRepository(),
            config.getAsyncStepStateRepository(),
            config.getSuspensionDataRepository(),
            config.getStateRepository(),
            config.getChatStore()
        );
    }

    @Test
    public void testSystemSchemaInSuspendedWorkflow() throws Exception {
        // Build workflow that suspends with system schema
        WorkflowGraph<UserRequest, WorkflowResult> workflow = WorkflowBuilder
            .define("system-workflow", UserRequest.class, WorkflowResult.class)
            .then(StepDefinition.of("processRequest", SystemWorkflowSteps::processRequest))
            .then(StepDefinition.of("suspendWithSystemPrompt", SystemWorkflowSteps::suspendWithSystemPrompt))
            .then(StepDefinition.of("handleSystemInput", SystemWorkflowSteps::handleSystemInput))
            .then(StepDefinition.of("complete", SystemWorkflowSteps::complete))
            .build();
        
        engine.register(workflow);
        
        // Create initial request
        ChatRequest request = new ChatRequest();
        request.setId(UUID.randomUUID().toString());
        request.setChatId("chat-system-test");
        request.setUserId("user-123");
        request.setWorkflowId("system-workflow");
        request.setType(ChatRequest.MessageType.USER);
        request.setLanguage(Language.ENGLISH);
        request.setTimestamp(System.currentTimeMillis());
        request.setPropertiesMap(Map.of("action", "start-system-process"));
        request.setRequestSchemaName("UserRequest");
        
        // Execute workflow - should suspend with system schema
        ChatResponse response = workflowService.executeChat(request);
        
        assertNotNull(response);
        assertTrue(response.isCompleted());
        assertNotNull(response.getNextSchema());
        assertEquals("system.configuration", response.getNextSchema().getSchemaName());
        
        // Convert to ChatMessageTask and verify system flag
        List<ChatMessageTask> tasks = workflowService.convertMessageToTasks(response);
        assertEquals(1, tasks.size());
        
        ChatMessageTask task = tasks.get(0);
        assertNotNull(task.getSystem());
        assertTrue(Boolean.TRUE.equals(task.getSystem()), "Task should have system=true for @SchemaSystem schema");
        
        // Verify the schema itself
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(SystemConfigurationInput.class);
        assertTrue(schema.isSystem(), "Schema should be marked as system");
    }

    @Test
    public void testNonSystemSchemaInSuspendedWorkflow() throws Exception {
        // Build workflow that suspends with regular schema
        WorkflowGraph<UserRequest, WorkflowResult> workflow = WorkflowBuilder
            .define("user-workflow", UserRequest.class, WorkflowResult.class)
            .then(StepDefinition.of("processRequest", SystemWorkflowSteps::processRequest))
            .then(StepDefinition.of("suspendWithUserPrompt", SystemWorkflowSteps::suspendWithUserPrompt))
            .then(StepDefinition.of("handleUserInput", SystemWorkflowSteps::handleUserInput))
            .then(StepDefinition.of("complete", SystemWorkflowSteps::complete))
            .build();
        
        engine.register(workflow);
        
        // Create initial request
        ChatRequest request = new ChatRequest();
        request.setId(UUID.randomUUID().toString());
        request.setChatId("chat-user-test");
        request.setUserId("user-456");
        request.setWorkflowId("user-workflow");
        request.setType(ChatRequest.MessageType.USER);
        request.setLanguage(Language.ENGLISH);
        request.setTimestamp(System.currentTimeMillis());
        request.setPropertiesMap(Map.of("action", "start-user-process"));
        request.setRequestSchemaName("UserRequest");
        
        // Execute workflow - should suspend with regular schema
        ChatResponse response = workflowService.executeChat(request);
        
        assertNotNull(response);
        assertTrue(response.isCompleted());
        assertNotNull(response.getNextSchema());
        assertEquals("user.feedback", response.getNextSchema().getSchemaName());
        
        // Convert to ChatMessageTask and verify system flag
        List<ChatMessageTask> tasks = workflowService.convertMessageToTasks(response);
        assertEquals(1, tasks.size());
        
        ChatMessageTask task = tasks.get(0);
        // For non-system schemas, system should be null (which defaults to false)
        assertNull(task.getSystem(), "Task should have system=null for regular schema");
        
        // Verify the schema itself
        AIFunctionSchema schema = SchemaUtils.getSchemaFromClass(UserFeedbackInput.class);
        assertFalse(schema.isSystem(), "Schema should not be marked as system");
    }

    @Test
    public void testMixedSchemaWorkflow() throws Exception {
        // Build workflow with both system and non-system schemas
        // For simplicity, we'll just test the two different paths separately
        
        // System path workflow
        WorkflowGraph<UserRequest, WorkflowResult> systemWorkflow = WorkflowBuilder
            .define("system-workflow-2", UserRequest.class, WorkflowResult.class)
            .then(StepDefinition.of("processRequest", SystemWorkflowSteps::processRequest))
            .then(StepDefinition.of("suspendWithSystemPrompt", SystemWorkflowSteps::suspendWithSystemPrompt))
            .then(StepDefinition.of("handleSystemInput", SystemWorkflowSteps::handleSystemInput))
            .then(StepDefinition.of("complete", SystemWorkflowSteps::complete))
            .build();
            
        // User path workflow    
        WorkflowGraph<UserRequest, WorkflowResult> userWorkflow = WorkflowBuilder
            .define("user-workflow-2", UserRequest.class, WorkflowResult.class)
            .then(StepDefinition.of("processRequest", SystemWorkflowSteps::processRequest))
            .then(StepDefinition.of("suspendWithUserPrompt", SystemWorkflowSteps::suspendWithUserPrompt))
            .then(StepDefinition.of("handleUserInput", SystemWorkflowSteps::handleUserInput))
            .then(StepDefinition.of("complete", SystemWorkflowSteps::complete))
            .build();
        
        engine.register(systemWorkflow);
        engine.register(userWorkflow);
        
        // Test system path
        ChatRequest systemRequest = new ChatRequest();
        systemRequest.setId(UUID.randomUUID().toString());
        systemRequest.setChatId("chat-mixed-system");
        systemRequest.setUserId("user-789");
        systemRequest.setWorkflowId("system-workflow-2");
        systemRequest.setType(ChatRequest.MessageType.USER);
        systemRequest.setLanguage(Language.ENGLISH);
        systemRequest.setTimestamp(System.currentTimeMillis());
        systemRequest.setPropertiesMap(Map.of("action", "system-config", "type", "system"));
        systemRequest.setRequestSchemaName("UserRequest");
        
        ChatResponse systemResponse = workflowService.executeChat(systemRequest);
        List<ChatMessageTask> systemTasks = workflowService.convertMessageToTasks(systemResponse);
        assertTrue(Boolean.TRUE.equals(systemTasks.get(0).getSystem()), "System path should produce system task");
        
        // Test user path
        ChatRequest userRequest = new ChatRequest();
        userRequest.setId(UUID.randomUUID().toString());
        userRequest.setChatId("chat-mixed-user");
        userRequest.setUserId("user-789");
        userRequest.setWorkflowId("user-workflow-2");
        userRequest.setType(ChatRequest.MessageType.USER);
        userRequest.setLanguage(Language.ENGLISH);
        userRequest.setTimestamp(System.currentTimeMillis());
        userRequest.setPropertiesMap(Map.of("action", "user-feedback", "type", "user"));
        userRequest.setRequestSchemaName("UserRequest");
        
        ChatResponse userResponse = workflowService.executeChat(userRequest);
        List<ChatMessageTask> userTasks = workflowService.convertMessageToTasks(userResponse);
        assertFalse(Boolean.TRUE.equals(userTasks.get(0).getSystem()), "User path should not produce system task");
    }

    // Step implementations
    public static class SystemWorkflowSteps {
        
        public static StepResult<ProcessedRequest> processRequest(UserRequest request) {
            log.info("Processing request: {}", request);
            ProcessedRequest processed = new ProcessedRequest(request.getAction(), request.getMetadata());
            return StepResult.continueWith(processed);
        }
        
        public static String checkRequestType(ProcessedRequest request) {
            return request.getMetadata().getOrDefault("type", "user");
        }
        
        public static StepResult suspendWithSystemPrompt(ProcessedRequest request) {
            log.info("Suspending with system prompt");
            SystemPrompt prompt = new SystemPrompt(
                "System configuration required",
                "Please provide system configuration parameters"
            );
            return StepResult.suspend(prompt, SystemConfigurationInput.class);
        }
        
        public static StepResult suspendWithUserPrompt(ProcessedRequest request) {
            log.info("Suspending with user prompt");
            UserPrompt prompt = new UserPrompt(
                "User feedback needed",
                "Please provide your feedback"
            );
            return StepResult.suspend(prompt, UserFeedbackInput.class);
        }
        
        public static StepResult<ConfiguredSystem> handleSystemInput(SystemConfigurationInput input) {
            log.info("Handling system input: {}", input);
            ConfiguredSystem configured = new ConfiguredSystem(input.getConfigKey(), input.getConfigValue());
            return StepResult.continueWith(configured);
        }
        
        public static StepResult<UserFeedback> handleUserInput(UserFeedbackInput input) {
            log.info("Handling user input: {}", input);
            UserFeedback feedback = new UserFeedback(input.getFeedback(), input.getRating());
            return StepResult.continueWith(feedback);
        }
        
        public static StepResult<WorkflowResult> complete(Object input) {
            log.info("Completing workflow with input: {}", input);
            String resultType = input instanceof ConfiguredSystem ? "system" : "user";
            WorkflowResult result = new WorkflowResult(true, resultType, Map.of("result", input.toString()));
            return StepResult.finish(result);
        }
    }

    // Domain objects
    @SchemaName("UserRequest")
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserRequest {
        private String action;
        private Map<String, String> metadata = new HashMap<>();
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessedRequest {
        private String action;
        private Map<String, String> metadata;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SystemPrompt {
        private String title;
        private String message;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserPrompt {
        private String title;
        private String message;
    }
    
    @SchemaSystem
    @SchemaName("system.configuration")
    @SchemaDescription("System configuration parameters")
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SystemConfigurationInput {
        private String configKey;
        private String configValue;
    }
    
    @SchemaName("user.feedback")
    @SchemaDescription("User feedback form")
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserFeedbackInput {
        private String feedback;
        private Integer rating;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConfiguredSystem {
        private String key;
        private String value;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserFeedback {
        private String feedback;
        private Integer rating;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkflowResult {
        private boolean success;
        private String type;
        private Map<String, Object> data;
    }
}