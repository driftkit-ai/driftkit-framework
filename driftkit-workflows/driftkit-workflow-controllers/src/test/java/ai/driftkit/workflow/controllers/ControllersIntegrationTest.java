package ai.driftkit.workflow.controllers;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.PromptRequest;
import ai.driftkit.common.domain.PromptRequest.PromptIdRequest;
import ai.driftkit.workflow.controllers.controller.AnalyticsController;
import ai.driftkit.workflow.controllers.controller.AsyncModelRequestController;
import ai.driftkit.workflow.controllers.controller.ModelRequestController;
import ai.driftkit.workflow.controllers.controller.WorkflowManagementController;
import ai.driftkit.workflow.controllers.service.WorkflowAnalyticsService;
import ai.driftkit.workflow.controllers.service.AsyncTaskService;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.TextRequest;
import ai.driftkit.workflow.engine.spring.tracing.repository.AsyncTaskRepository;
import ai.driftkit.workflow.engine.spring.tracing.repository.CoreModelRequestTraceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Integration test for workflow controllers with MongoDB.
 * 
 * REQUIREMENTS:
 * - MongoDB MUST be running on localhost:27017
 * - All repositories, services and controllers MUST be created
 * 
 * To run this test:
 * 1. Start MongoDB: docker run -d -p 27017:27017 mongo:latest
 * 2. Run test: mvn test -Dtest=ControllersIntegrationTest -DfailIfNoTests=false
 * 
 * This test is disabled by default to not break CI/CD pipelines.
 * Enable it manually when you need to verify MongoDB integration.
 */
@SpringBootTest(
    classes = {test.app.TestApplication.class, TestApplicationConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
// @Disabled("Requires external MongoDB on localhost:27017. Enable manually for local testing.")
public class ControllersIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private AsyncTaskRepository asyncTaskRepository;
    
    @Autowired
    private CoreModelRequestTraceRepository traceRepository;
    
    @Autowired
    private AsyncTaskService asyncTaskService;
    
    @Autowired
    private WorkflowAnalyticsService analyticsService;
    
    @Autowired
    private AsyncModelRequestController asyncModelRequestController;
    
    @Autowired
    private ModelRequestController modelRequestController;
    
    @Autowired
    private AnalyticsController analyticsController;
    
    @Autowired
    private WorkflowManagementController workflowController;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    public void testAllRequiredBeansCreated() {
        // Verify all MongoDB repositories are created
        assertNotNull(asyncTaskRepository, "AsyncTaskRepository MUST be available with MongoDB");
        assertNotNull(traceRepository, "ModelRequestTraceRepository MUST be available with MongoDB");
        
        // Verify all services are created
        assertNotNull(asyncTaskService, "AsyncTaskService MUST be created");
        assertNotNull(analyticsService, "WorkflowAnalyticsService MUST be created");
        
        // Verify all controllers are created
        assertNotNull(asyncModelRequestController, "AsyncModelRequestController MUST be created");
        assertNotNull(modelRequestController, "ModelRequestController MUST be created");
        assertNotNull(analyticsController, "AnalyticsController MUST be created");
        assertNotNull(workflowController, "WorkflowManagementController MUST be created");
        
        System.out.println("✅ All required beans successfully created with MongoDB");
    }
    
    @Test
    public void testAsyncTaskRepositoryOperations() {
        // Test that we can perform operations on AsyncTaskRepository
        long count = asyncTaskRepository.count();
        System.out.println("AsyncTaskRepository count: " + count);
        assertNotNull(count, "Should be able to count documents");
    }
    
    @Test
    public void testModelRequestTraceRepositoryOperations() {
        // Test that we can perform operations on ModelRequestTraceRepository
        long count = traceRepository.count();
        System.out.println("ModelRequestTraceRepository count: " + count);
        assertNotNull(count, "Should be able to count documents");
    }
    
    @Test
    public void testAsyncTextRequestEndpoint() throws Exception {
        TextRequest request = new TextRequest();
        request.setText("Integration test message");
        request.setModelId("gpt-3.5-turbo");
        request.setTemperature(0.7);
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        mockMvc.perform(post("/api/v1/model/async/text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .header("X-User-Id", "integration-test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
        
        System.out.println("✅ Async text request endpoint working");
    }
    
    @Test
    public void testAsyncPromptRequestEndpoint() throws Exception {
        Map<String, Object> variables = new HashMap<>();
        variables.put("input", "Test input");
        
        PromptIdRequest promptIdRequest = new PromptIdRequest();
        promptIdRequest.setPromptId("test.prompt");
        promptIdRequest.setTemperature(0.7);
        
        PromptRequest request = new PromptRequest(
                promptIdRequest, 
                "test-chat-id", 
                variables, 
                Language.ENGLISH
        );
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        mockMvc.perform(post("/api/v1/model/async/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .header("X-User-Id", "integration-test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
        
        System.out.println("✅ Async prompt request endpoint working");
    }
    
    @Test
    public void testWorkflowEndpoints() throws Exception {
        // Test list workflows
        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk());
        
        System.out.println("✅ Workflow list endpoint working");
    }
    
    //@Test
    public void testAnalyticsEndpoints() throws Exception {
        // Test analytics prompt methods endpoint
        mockMvc.perform(get("/data/v1.0/analytics/prompts/methods"))
                .andExpect(status().isOk());
        
        // Test daily metrics endpoint
        mockMvc.perform(get("/data/v1.0/analytics/metrics/daily"))
                .andExpect(status().isOk());
        
        System.out.println("✅ Analytics endpoints working");
    }
    
    @Test
    public void testTaskStatusEndpoint() throws Exception {
        // First create a task
        TextRequest request = new TextRequest();
        request.setText("Test for status check");
        request.setModelId("gpt-3.5-turbo");
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        var result = mockMvc.perform(post("/api/v1/model/async/text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        String taskId = (String) response.get("taskId");
        
        assertNotNull(taskId, "Task ID should be returned");
        
        // Check task status
        mockMvc.perform(get("/api/v1/model/async/task/" + taskId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId));
        
        System.out.println("✅ Task status endpoint working with taskId: " + taskId);
    }
    
    @Test
    public void testAllControllersRegistered() {
        // Verify all controller beans are registered in application context
        String[] controllerBeans = applicationContext.getBeanNamesForType(Object.class);
        
        boolean hasAsyncController = false;
        boolean hasModelController = false;
        boolean hasAnalyticsController = false;
        boolean hasWorkflowController = false;
        
        for (String beanName : controllerBeans) {
            if (beanName.contains("AsyncModelRequestController") || beanName.contains("asyncModelRequestController")) {
                hasAsyncController = true;
            }
            if (beanName.contains("ModelRequestController") || beanName.contains("modelRequestController")) {
                hasModelController = true;
            }
            if (beanName.contains("AnalyticsController") || beanName.contains("analyticsController")) {
                hasAnalyticsController = true;
            }
            if (beanName.contains("WorkflowManagementController") || beanName.contains("workflowManagementController")) {
                hasWorkflowController = true;
            }
        }
        
        assertTrue(hasAsyncController, "AsyncModelRequestController must be registered");
        assertTrue(hasModelController, "ModelRequestController must be registered");
        assertTrue(hasAnalyticsController, "AnalyticsController must be registered");
        assertTrue(hasWorkflowController, "WorkflowManagementController must be registered");
        
        System.out.println("✅ All controllers properly registered in Spring context");
    }
}