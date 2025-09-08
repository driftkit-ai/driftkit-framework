package ai.driftkit.workflow.controllers.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.common.domain.PromptRequest;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.workflow.engine.agent.AgentResponse;
import ai.driftkit.workflow.engine.agent.LLMAgent;
import ai.driftkit.workflow.engine.agent.RequestTracingProvider;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.core.WorkflowEngine.WorkflowExecution;
import ai.driftkit.workflow.controllers.controller.ModelRequestController;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.TextRequest;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity.TaskStatus;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity.TaskType;
import ai.driftkit.workflow.engine.spring.tracing.repository.AsyncTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing async task execution.
 * This is the SINGLE source of truth for LLM request execution logic.
 * All controllers should use this service instead of duplicating logic.
 * Only activated when AsyncTaskRepository is available (requires MongoDB).
 */
@Slf4j
@Service
@ConditionalOnBean(MongoTemplate.class)
@RequiredArgsConstructor
public class AsyncTaskService {
    
    private final AsyncTaskRepository asyncTaskRepository;
    private final ModelClient modelClient;
    private final PromptService promptService;
    private final RequestTracingProvider tracingProvider;
    private final ChatStore chatStore;
    private final WorkflowEngine workflowEngine;
    
    /**
     * Execute prompt request synchronously (for ModelRequestController)
     */
    public AgentResponse<?> executePromptRequestSync(PromptRequest request) {
        // Check if we should use workflow
        if (StringUtils.isNotBlank(request.getWorkflow())) {
            // For sync calls with workflow, create async task and wait
            String taskId = executePromptRequestAsync(request, "system");
            return waitForTaskCompletion(taskId);
        }
        
        // Execute directly with LLMAgent
        return executePromptWithAgent(request);
    }
    
    /**
     * Execute text request synchronously (for ModelRequestController)
     */
    public AgentResponse<?> executeTextRequestSync(TextRequest request) {
        // Check if we should use workflow
        if (StringUtils.isNotBlank(request.getWorkflow())) {
            // For sync calls with workflow, create async task and wait
            String taskId = executeTextRequestAsync(request, "system");
            return waitForTaskCompletion(taskId);
        }
        
        // Execute directly with LLMAgent
        return executeTextWithAgent(request);
    }
    
    /**
     * Create and execute async prompt request
     */
    public String executePromptRequestAsync(PromptRequest request, String userId) {
        String taskId = UUID.randomUUID().toString();
        
        // Create task entity
        AsyncTaskEntity task = AsyncTaskEntity.builder()
                .taskId(taskId)
                .userId(userId)
                .chatId(request.getChatId())
                .status(TaskStatus.PENDING)
                .taskType(TaskType.PROMPT_REQUEST)
                .requestBody(toJsonSafe(request))
                .workflowId(request.getWorkflow())
                .variables(request.getVariables())
                .createdAt(System.currentTimeMillis())
                .build();
        
        // Extract prompt ID if available
        if (CollectionUtils.isNotEmpty(request.getPromptIds())) {
            task.setPromptId(request.getPromptIds().get(0).getPromptId());
        }
        
        // Save initial task
        asyncTaskRepository.save(task);
        
        // Execute asynchronously
        executePromptAsync(taskId, request);
        
        return taskId;
    }
    
    /**
     * Create and execute async text request
     */
    public String executeTextRequestAsync(TextRequest request, String userId) {
        String taskId = UUID.randomUUID().toString();
        
        // Create task entity
        AsyncTaskEntity task = AsyncTaskEntity.builder()
                .taskId(taskId)
                .userId(userId)
                .chatId(request.getChatId())
                .status(TaskStatus.PENDING)
                .taskType(TaskType.TEXT_REQUEST)
                .requestBody(toJsonSafe(request))
                .workflowId(request.getWorkflow())
                .variables(request.getVariables())
                .modelId(request.getModelId())
                .temperature(request.getTemperature())
                .createdAt(System.currentTimeMillis())
                .build();
        
        // Save initial task
        asyncTaskRepository.save(task);
        
        // Execute asynchronously
        executeTextAsync(taskId, request);
        
        return taskId;
    }
    
    /**
     * Execute prompt request asynchronously
     */
    @Async("taskExecutor")
    protected void executePromptAsync(String taskId, PromptRequest request) {
        // Update status to running
        updateTaskStatus(taskId, TaskStatus.RUNNING);
        
        try {
            // Check if we should use workflow
            if (StringUtils.isNotBlank(request.getWorkflow())) {
                // Execute workflow asynchronously
                executePromptWithWorkflowAsync(taskId, request);
            } else {
                // Execute with LLMAgent directly
                AgentResponse<?> response = executePromptWithAgent(request);
                completeTask(taskId, response);
            }
        } catch (Exception e) {
            log.error("Error executing async prompt request: " + taskId, e);
            failTask(taskId, e);
        }
    }
    
    /**
     * Execute text request asynchronously
     */
    @Async("taskExecutor")
    protected void executeTextAsync(String taskId, TextRequest request) {
        // Update status to running
        updateTaskStatus(taskId, TaskStatus.RUNNING);
        
        try {
            // Check if we should use workflow
            if (StringUtils.isNotBlank(request.getWorkflow())) {
                // Execute workflow asynchronously
                executeTextWithWorkflowAsync(taskId, request);
            } else {
                // Execute with LLMAgent directly
                AgentResponse<?> response = executeTextWithAgent(request);
                completeTask(taskId, response);
            }
        } catch (Exception e) {
            log.error("Error executing async text request: " + taskId, e);
            failTask(taskId, e);
        }
    }
    
    /**
     * Execute prompt request with workflow engine asynchronously
     */
    private void executePromptWithWorkflowAsync(String taskId, PromptRequest request) {
        try {
            // Execute workflow with PromptRequest as input
            WorkflowExecution<?> execution = workflowEngine.execute(request.getWorkflow(), request);
            
            // Register completion handler
            execution.getFuture().whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Workflow execution failed for task: " + taskId, error);
                    failTask(taskId, new Exception("Workflow execution failed", error));
                } else {
                    // Convert result to AgentResponse
                    AgentResponse<?> response;
                    if (result instanceof AgentResponse) {
                        response = (AgentResponse<?>) result;
                    } else {
                        response = AgentResponse.structured(result);
                    }
                    completeTask(taskId, response);
                }
            });
            
        } catch (Exception e) {
            log.error("Error starting workflow for task: " + taskId, e);
            failTask(taskId, e);
        }
    }
    
    /**
     * Execute text request with workflow engine asynchronously
     */
    private void executeTextWithWorkflowAsync(String taskId, TextRequest request) {
        try {
            // Execute workflow with TextRequest as input
            WorkflowExecution<?> execution = workflowEngine.execute(request.getWorkflow(), request);
            
            // Register completion handler
            execution.getFuture().whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Workflow execution failed for task: " + taskId, error);
                    failTask(taskId, new Exception("Workflow execution failed", error));
                } else {
                    // Convert result to AgentResponse
                    AgentResponse<?> response;
                    if (result instanceof AgentResponse) {
                        response = (AgentResponse<?>) result;
                    } else {
                        response = AgentResponse.structured(result);
                    }
                    completeTask(taskId, response);
                }
            });
            
        } catch (Exception e) {
            log.error("Error starting workflow for task: " + taskId, e);
            failTask(taskId, e);
        }
    }
    
    /**
     * Execute prompt request with LLMAgent
     * This is the SINGLE implementation of prompt execution logic
     */
    private AgentResponse<?> executePromptWithAgent(PromptRequest request) {
        // Validate request
        if (CollectionUtils.isEmpty(request.getPromptIds())) {
            throw new IllegalArgumentException("promptIds must be provided");
        }
        
        // Get first prompt
        PromptRequest.PromptIdRequest promptIdRequest = request.getPromptIds().get(0);
        String promptId = promptIdRequest.getPromptId();
        
        if (StringUtils.isBlank(promptId)) {
            throw new IllegalArgumentException("promptId must be provided");
        }
        
        // Get prompt from service
        Language language = request.getLanguage() != null ? request.getLanguage() : Language.GENERAL;
        Prompt prompt = promptService.getCurrentPromptOrThrow(promptId, language);
        
        // Handle savePrompt flag
        if (request.isSavePrompt() && StringUtils.isNotBlank(promptIdRequest.getPrompt())) {
            prompt.setMessage(promptIdRequest.getPrompt());
            prompt.setUpdatedTime(System.currentTimeMillis());
            if (promptIdRequest.getTemperature() != null) {
                prompt.setTemperature(promptIdRequest.getTemperature());
            }
            promptService.savePrompt(prompt);
        }
        
        // Use prompt text from request if provided
        String promptText = StringUtils.isNotBlank(promptIdRequest.getPrompt()) 
            ? promptIdRequest.getPrompt() 
            : prompt.getMessage();
        
        // Apply variables
        Map<String, Object> variables = request.getVariables();
        if (MapUtils.isNotEmpty(variables)) {
            promptText = PromptUtils.applyVariables(promptText, variables);
        }
        
        // Determine chatId
        String chatId = StringUtils.isNotBlank(request.getChatId()) ? request.getChatId() : UUID.randomUUID().toString();
        
        // Create LLMAgent
        var agentBuilder = LLMAgent.builder()
            .modelClient(modelClient)
            .name("api-request")
            .agentId(UUID.randomUUID().toString())
            .chatId(chatId)
            .workflowType(request.getWorkflow())
            .promptService(promptService)
            .tracingProvider(tracingProvider)
            .chatStore(chatStore);
        
        // Set temperature
        if (promptIdRequest.getTemperature() != null) {
            agentBuilder.temperature(promptIdRequest.getTemperature());
        } else if (prompt.getTemperature() != null) {
            agentBuilder.temperature(prompt.getTemperature());
        } else {
            agentBuilder.temperature(modelClient.getTemperature());
        }
        
        // Set model
        if (StringUtils.isNotBlank(request.getModelId())) {
            agentBuilder.model(request.getModelId());
        } else if (StringUtils.isNotBlank(prompt.getModelId())) {
            agentBuilder.model(prompt.getModelId());
        } else {
            agentBuilder.model(modelClient.getModel());
        }
        
        // Set system message
        if (StringUtils.isNotBlank(prompt.getSystemMessage())) {
            String systemMessage = prompt.getSystemMessage();
            if (MapUtils.isNotEmpty(variables)) {
                systemMessage = PromptUtils.applyVariables(systemMessage, variables);
            }
            agentBuilder.systemMessage(systemMessage);
        }
        
        LLMAgent agent = agentBuilder.build();
        
        // Get response format
        ResponseFormat responseFormat = request.getResponseFormat();
        
        // Determine request type
        if (responseFormat != null && responseFormat.getType() == ResponseFormat.ResponseType.IMAGE) {
            return agent.executeImageGeneration(promptText, variables);
        }
        
        // Check for images
        if (CollectionUtils.isNotEmpty(request.getImageBase64())) {
            List<byte[]> imageDataList = new ArrayList<>();
            for (String base64Image : request.getImageBase64()) {
                byte[] imageData = Base64.getDecoder().decode(base64Image);
                imageDataList.add(imageData);
            }
            return agent.executeWithImages(promptText, imageDataList, variables);
        }
        
        // Regular text request
        return agent.executeText(promptText, variables);
    }
    
    /**
     * Execute text request with LLMAgent
     * This is the SINGLE implementation of text execution logic
     */
    private AgentResponse<?> executeTextWithAgent(TextRequest request) {
        // Validate request
        if (StringUtils.isBlank(request.getText())) {
            throw new IllegalArgumentException("text must be provided");
        }
        
        // Determine chatId
        String chatId = StringUtils.isNotBlank(request.getChatId()) ? request.getChatId() : UUID.randomUUID().toString();
        
        // Apply variables to text
        String text = request.getText();
        Map<String, Object> variables = request.getVariables();
        if (MapUtils.isNotEmpty(variables)) {
            text = PromptUtils.applyVariables(text, variables);
        }
        
        // Create LLMAgent
        var agentBuilder = LLMAgent.builder()
            .modelClient(modelClient)
            .name("text-request")
            .agentId(UUID.randomUUID().toString())
            .chatId(chatId)
            .workflowType(request.getWorkflow())
            .promptService(promptService)
            .tracingProvider(tracingProvider)
            .chatStore(chatStore);
        
        // Set temperature
        if (request.getTemperature() != null) {
            agentBuilder.temperature(request.getTemperature());
        } else {
            agentBuilder.temperature(modelClient.getTemperature());
        }
        
        // Set model
        if (StringUtils.isNotBlank(request.getModelId())) {
            agentBuilder.model(request.getModelId());
        } else {
            agentBuilder.model(modelClient.getModel());
        }
        
        // Set system message
        if (StringUtils.isNotBlank(request.getSystemMessage())) {
            String systemMessage = request.getSystemMessage();
            if (MapUtils.isNotEmpty(variables)) {
                systemMessage = PromptUtils.applyVariables(systemMessage, variables);
            }
            agentBuilder.systemMessage(systemMessage);
        }
        
        LLMAgent agent = agentBuilder.build();
        
        // Check response format
        ResponseFormat responseFormat = request.getResponseFormat();
        if (responseFormat != null && responseFormat.getType() == ResponseFormat.ResponseType.IMAGE) {
            return agent.executeImageGeneration(text, variables);
        }
        
        // Check for images
        if (CollectionUtils.isNotEmpty(request.getImages())) {
            List<byte[]> imageDataList = new ArrayList<>();
            for (String base64Image : request.getImages()) {
                byte[] imageData = Base64.getDecoder().decode(base64Image);
                imageDataList.add(imageData);
            }
            return agent.executeWithImages(text, imageDataList, variables);
        }
        
        // Regular text request
        return agent.executeText(text, variables);
    }
    
    /**
     * Get task status
     */
    public Optional<AsyncTaskEntity> getTask(String taskId) {
        return asyncTaskRepository.findByTaskId(taskId);
    }
    
    /**
     * Get task result as AgentResponse
     */
    public Optional<AgentResponse<?>> getTaskResult(String taskId) {
        Optional<AsyncTaskEntity> taskOpt = asyncTaskRepository.findByTaskId(taskId);
        
        if (taskOpt.isEmpty()) {
            return Optional.empty();
        }
        
        AsyncTaskEntity task = taskOpt.get();
        
        if (task.getStatus() != TaskStatus.COMPLETED) {
            return Optional.empty();
        }
        
        try {
            // Deserialize result
            AgentResponse<?> response = JsonUtils.fromJson(task.getResult(), AgentResponse.class);
            return Optional.of(response);
        } catch (Exception e) {
            log.error("Error deserializing task result: " + taskId, e);
            return Optional.of(AgentResponse.text("Failed to deserialize result"));
        }
    }
    
    /**
     * Cancel task
     */
    public boolean cancelTask(String taskId) {
        Optional<AsyncTaskEntity> taskOpt = asyncTaskRepository.findByTaskId(taskId);
        
        if (taskOpt.isEmpty()) {
            return false;
        }
        
        AsyncTaskEntity task = taskOpt.get();
        
        if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.RUNNING) {
            task.setStatus(TaskStatus.CANCELLED);
            task.setCompletedAt(System.currentTimeMillis());
            asyncTaskRepository.save(task);
            return true;
        }
        
        return false;
    }
    
    /**
     * Wait for task completion (used for sync operations)
     */
    private AgentResponse<?> waitForTaskCompletion(String taskId) {
        int maxAttempts = 300; // 5 minutes with 1 second intervals
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            Optional<AsyncTaskEntity> taskOpt = asyncTaskRepository.findByTaskId(taskId);
            
            if (taskOpt.isEmpty()) {
                return AgentResponse.text("Task not found: " + taskId);
            }
            
            AsyncTaskEntity task = taskOpt.get();
            
            if (task.getStatus() == TaskStatus.COMPLETED) {
                return getTaskResult(taskId).orElse(AgentResponse.text("Failed to get result"));
            }
            
            if (task.getStatus() == TaskStatus.FAILED) {
                return AgentResponse.text(task.getErrorMessage());
            }
            
            if (task.getStatus() == TaskStatus.CANCELLED) {
                return AgentResponse.text("Task was cancelled");
            }
            
            try {
                Thread.sleep(1000); // Wait 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return AgentResponse.text("Interrupted while waiting for task");
            }
            
            attempts++;
        }
        
        return AgentResponse.text("Task timeout after 5 minutes");
    }
    
    /**
     * Update task status
     */
    private void updateTaskStatus(String taskId, TaskStatus status) {
        Optional<AsyncTaskEntity> taskOpt = asyncTaskRepository.findByTaskId(taskId);
        
        if (taskOpt.isPresent()) {
            AsyncTaskEntity task = taskOpt.get();
            task.setStatus(status);
            if (status == TaskStatus.RUNNING) {
                task.setStartedAt(System.currentTimeMillis());
            }
            asyncTaskRepository.save(task);
        }
    }
    
    /**
     * Complete task with success
     */
    private void completeTask(String taskId, AgentResponse<?> response) {
        Optional<AsyncTaskEntity> taskOpt = asyncTaskRepository.findByTaskId(taskId);
        
        if (taskOpt.isPresent()) {
            AsyncTaskEntity task = taskOpt.get();
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(System.currentTimeMillis());
            if (task.getStartedAt() != null) {
                task.setExecutionTimeMs(task.getCompletedAt() - task.getStartedAt());
            }
            task.setResult(toJsonSafe(response));
            asyncTaskRepository.save(task);
        }
    }
    
    /**
     * Fail task with error
     */
    private void failTask(String taskId, Exception e) {
        Optional<AsyncTaskEntity> taskOpt = asyncTaskRepository.findByTaskId(taskId);
        
        if (taskOpt.isPresent()) {
            AsyncTaskEntity task = taskOpt.get();
            task.setStatus(TaskStatus.FAILED);
            task.setCompletedAt(System.currentTimeMillis());
            if (task.getStartedAt() != null) {
                task.setExecutionTimeMs(task.getCompletedAt() - task.getStartedAt());
            }
            task.setErrorMessage(e.getMessage());
            task.setErrorStackTrace(getStackTrace(e));
            asyncTaskRepository.save(task);
        }
    }
    
    /**
     * Rate a task
     */
    public Optional<AsyncTaskEntity> rateTask(String taskId, Integer grade, String comment) {
        Optional<AsyncTaskEntity> taskOpt = asyncTaskRepository.findByTaskId(taskId);
        
        if (taskOpt.isEmpty()) {
            return Optional.empty();
        }
        
        AsyncTaskEntity task = taskOpt.get();
        
        // Store rating in metadata
        if (task.getMetadata() == null) {
            task.setMetadata(new HashMap<>());
        }
        task.getMetadata().put("grade", grade != null ? grade.toString() : null);
        task.getMetadata().put("gradeComment", comment);
        task.getMetadata().put("ratedAt", String.valueOf(System.currentTimeMillis()));
        
        asyncTaskRepository.save(task);
        return Optional.of(task);
    }
    
    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Safe JSON conversion that catches JsonProcessingException
     */
    private String toJsonSafe(Object obj) {
        try {
            return JsonUtils.toJson(obj);
        } catch (Exception e) {
            log.error("Error converting object to JSON", e);
            return "{}";
        }
    }
}