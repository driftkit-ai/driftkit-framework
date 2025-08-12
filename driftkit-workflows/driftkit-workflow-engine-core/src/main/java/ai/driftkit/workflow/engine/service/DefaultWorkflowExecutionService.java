package ai.driftkit.workflow.engine.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import ai.driftkit.workflow.engine.chat.ChatDomain.*;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.chat.converter.ChatMessageTaskConverter;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.AsyncStepState;
import ai.driftkit.workflow.engine.domain.StepMetadata;
import ai.driftkit.workflow.engine.domain.SuspensionData;
import ai.driftkit.workflow.engine.domain.WorkflowDetails;
import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.graph.WorkflowGraph;
import ai.driftkit.workflow.engine.persistence.MemoryManagementService;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of WorkflowExecutionService.
 * Core business logic extracted from Spring-specific WorkflowService.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultWorkflowExecutionService implements WorkflowExecutionService {
    
    private static final int MAX_WAIT_ITERATIONS = 10000; // 100 seconds max wait
    private static final long WAIT_INTERVAL_MS = 10;
    
    private final WorkflowEngine engine;
    private final SchemaProvider schemaProvider;
    private final MemoryManagementService memoryService;
    
    // Optional event publisher for WebSocket notifications
    private WorkflowEventPublisher eventPublisher;
    
    /**
     * Sets the event publisher for WebSocket/event notifications.
     * This is optional and can be set by the Spring layer if needed.
     */
    public void setEventPublisher(WorkflowEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    // ========== Chat Workflow Execution ==========
    
    @Override
    public ChatResponse executeChat(ChatRequest request) {
        try {
            // Store the request in chat history
            memoryService.storeChatRequest(request);

            // Determine workflow to use
            String workflowId = request.getWorkflowId();
            if (workflowId == null) {
                throw new IllegalArgumentException("No workflow specified in request");
            }

            // Execute workflow with the chat request
            var execution = engine.execute(workflowId, request);
            String runId = execution.getRunId();
            
            // Notify event publisher if available
            if (eventPublisher != null) {
                eventPublisher.publishWorkflowStarted(runId, workflowId);
            }
            
            // Wait for workflow to reach a terminal state
            WorkflowInstance instance = waitForTerminalState(runId);
            
            log.info("Workflow {} reached state: {}, suspensionData: {}", 
                runId, instance.getStatus(), instance.getSuspensionData());
            
            // Create ChatResponse based on workflow state
            ChatResponse response = createChatResponseFromWorkflowState(
                    request.getChatId(),
                    request.getUserId(),
                    request.getLanguage(),
                    workflowId,
                    instance
            );

            // Update chat history with response
            memoryService.storeChatResponse(response);
            
            // If it's an async response, also store the async step state for polling
            if (!response.isCompleted() && instance.getSuspensionData() != null) {
                String messageId = instance.getSuspensionData().messageId();
                Optional<AsyncStepState> asyncStateOpt = memoryService.getAsyncStepState(messageId);
                if (asyncStateOpt.isPresent()) {
                    // This is async - store for polling
                    memoryService.updateAsyncResponse(response.getId(), response);
                }
            }

            return response;

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            // Return error response in proper ChatResponse format
            return createErrorResponse(request, e);
        }
    }
    
    @Override
    public ChatResponse resumeChat(String messageId, ChatRequest request) {
        try {
            // Find the suspended workflow instance by messageId
            Optional<WorkflowInstance> instanceOpt = engine.findInstanceByMessageId(messageId);
            if (!instanceOpt.isPresent()) {
                throw new IllegalArgumentException("No suspended workflow found for messageId: " + messageId);
            }
            
            WorkflowInstance instance = instanceOpt.get();
            if (instance.getStatus() != WorkflowInstance.WorkflowStatus.SUSPENDED) {
                throw new IllegalStateException("Workflow is not in suspended state: " + instance.getStatus());
            }
            
            // Get suspension data to validate input
            SuspensionData suspensionData = instance.getSuspensionData();
            if (suspensionData == null) {
                throw new IllegalStateException("No suspension data found for workflow");
            }
            
            // Resume the workflow with user input
            var execution = engine.resume(instance.getInstanceId(), request);
            String runId = execution.getRunId();
            
            // Notify event publisher if available
            if (eventPublisher != null) {
                eventPublisher.publishWorkflowResumed(runId, instance.getWorkflowId());
            }
            
            // Wait for workflow to reach next terminal state
            instance = waitForTerminalState(runId);
            
            // Create ChatResponse based on new workflow state
            ChatResponse response = createChatResponseFromWorkflowState(
                    request.getChatId(),
                    request.getUserId(),
                    request.getLanguage(),
                    instance.getWorkflowId(),
                    instance
            );
            
            // Update chat history with response
            memoryService.storeChatResponse(response);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error resuming chat for messageId: {}", messageId, e);
            return createErrorResponse(request, e);
        }
    }
    
    @Override
    public Optional<ChatResponse> getAsyncStatus(String messageId) {
        // First try to get from async response repository
        Optional<ChatResponse> asyncResponse = memoryService.getAsyncResponse(messageId);
        if (asyncResponse.isPresent()) {
            // Check if we need to update it from async step state
            Optional<AsyncStepState> asyncStateOpt = memoryService.getAsyncStepState(messageId);
            if (asyncStateOpt.isPresent()) {
                AsyncStepState asyncState = asyncStateOpt.get();
                ChatResponse response = asyncResponse.get();
                
                // Update progress
                response.setPercentComplete(asyncState.getPercentComplete());
                response.setCompleted(asyncState.isCompleted());
                
                Map<String, String> properties = new HashMap<>();
                
                // If 100% complete, use result data; otherwise use immediate data
                Object dataToUse = asyncState.getPercentComplete() == 100 && asyncState.getResultData() != null 
                    ? asyncState.getResultData() 
                    : asyncState.getInitialData();
                    
                if (dataToUse != null) {
                    properties = extractPropertiesFromData(dataToUse);
                }
                
                // Always add current status
                properties.put("status", asyncState.getStatusMessage());
                properties.put("progressPercent", String.valueOf(asyncState.getPercentComplete()));
                
                response.setPropertiesMap(properties);
                
                // Update in repository
                memoryService.updateAsyncResponse(messageId, response);
                
                return Optional.of(response);
            }
            return asyncResponse;
        }
        
        return Optional.empty();
    }
    
    // ========== Session Management ==========
    
    @Override
    public ChatSession getOrCreateSession(String chatId, String userId, String initialMessage) {
        return memoryService.getOrCreateChatSession(chatId, userId, initialMessage);
    }
    
    @Override
    public Optional<ChatSession> getChatSession(String chatId) {
        return memoryService.getChatSession(chatId);
    }
    
    @Override
    public ChatSession createChatSession(String userId, String name) {
        return memoryService.createChatSession(userId, name);
    }
    
    @Override
    public void archiveChatSession(String chatId) {
        memoryService.archiveChatSession(chatId);
    }
    
    @Override
    public PageResult<ChatSession> listChatsForUser(String userId, PageRequest pageRequest) {
        if (StringUtils.isEmpty(userId)) {
            return PageResult.empty(pageRequest.getPageNumber(), pageRequest.getPageSize());
        }
        return memoryService.listActiveChatsForUser(userId, pageRequest);
    }
    
    // ========== Chat History ==========
    
    @Override
    public PageResult<ChatMessage> getChatHistory(String chatId, PageRequest pageRequest, boolean includeContext) {
        return memoryService.getChatHistory(chatId, pageRequest, includeContext);
    }
    
    @Override
    public List<ChatMessageTask> convertMessageToTasks(ChatMessage message) {
        return ChatMessageTaskConverter.convert(message);
    }
    
    // ========== Workflow Management ==========
    
    @Override
    public List<WorkflowMetadata> listWorkflows() {
        return engine.getRegisteredWorkflows().stream()
                .map(workflowId -> {
                    var graph = engine.getWorkflowGraph(workflowId);
                    if (graph == null) {
                        return null;
                    }
                    return createWorkflowMetadata(graph);
                })
                .filter(Objects::nonNull)
                .toList();
    }
    
    @Override
    public WorkflowDetails getWorkflowDetails(String workflowId) {
        var graph = engine.getWorkflowGraph(workflowId);
        if (graph == null) {
            return null;
        }

        // Convert steps to metadata
        List<StepMetadata> steps = graph.nodes().values().stream()
                .map(this::convertToStepMetadata)
                .toList();

        AIFunctionSchema initialSchema = getInitialSchema(workflowId);
        WorkflowMetadata metadata = createWorkflowMetadata(graph);

        return new WorkflowDetails(
                metadata,
                steps,
                graph.initialStepId(),
                initialSchema
        );
    }
    
    @Override
    public AIFunctionSchema getInitialSchema(String workflowId) {
        var graph = engine.getWorkflowGraph(workflowId);
        if (graph == null) {
            return null;
        }

        // Get the initial step
        String initialStepId = graph.initialStepId();
        if (initialStepId == null) {
            return null;
        }

        StepNode initialStep = graph.nodes().get(initialStepId);
        if (initialStep == null || initialStep.executor() == null) {
            return null;
        }

        Class<?> inputType = initialStep.executor().getInputType();
        if (inputType == null || inputType == void.class) {
            return null;
        }

        return schemaProvider.generateSchema(inputType);
    }
    
    @Override
    public List<AIFunctionSchema> getWorkflowSchemas(String workflowId) {
        var graph = engine.getWorkflowGraph(workflowId);
        if (graph == null) {
            log.warn("Workflow not found: {}", workflowId);
            return List.of();
        }

        Set<AIFunctionSchema> uniqueSchemas = new HashSet<>();

        // Process each step in the workflow
        for (StepNode step : graph.nodes().values()) {
            try {
                // Get input schema from executor
                if (step.executor() != null) {
                    Class<?> inputType = step.executor().getInputType();
                    if (inputType != null && inputType != void.class && inputType != Void.class) {
                        AIFunctionSchema inputSchema = schemaProvider.generateSchema(inputType);
                        if (inputSchema != null) {
                            uniqueSchemas.add(inputSchema);
                        }
                    }
                }

                // Get output schema from executor
                Class<?> outputType = step.executor().getOutputType();
                if (outputType != null && outputType != void.class && outputType != Void.class) {
                    AIFunctionSchema outputSchema = schemaProvider.generateSchema(outputType);
                    if (outputSchema != null) {
                        uniqueSchemas.add(outputSchema);
                    }
                }
            } catch (Exception e) {
                log.error("Error generating schema for step {} in workflow {}", step.id(), workflowId, e);
            }
        }

        return new ArrayList<>(uniqueSchemas);
    }
    
    @Override
    public Optional<WorkflowInstance> getWorkflowState(String runId) {
        return engine.getWorkflowInstance(runId);
    }
    
    // ========== Private Helper Methods ==========
    
    private WorkflowInstance waitForTerminalState(String runId) throws InterruptedException {
        for (int i = 0; i < MAX_WAIT_ITERATIONS; i++) {
            Optional<WorkflowInstance> instanceOpt = engine.getWorkflowInstance(runId);
            if (!instanceOpt.isPresent()) {
                throw new IllegalStateException("Workflow instance not found: " + runId);
            }
            
            WorkflowInstance instance = instanceOpt.get();
            WorkflowInstance.WorkflowStatus status = instance.getStatus();
            
            log.debug("Workflow {} status: {}, currentStep: {}", runId, status, instance.getCurrentStepId());
            
            // Check for terminal states
            if (status == WorkflowInstance.WorkflowStatus.SUSPENDED ||
                status == WorkflowInstance.WorkflowStatus.COMPLETED ||
                status == WorkflowInstance.WorkflowStatus.FAILED) {
                return instance;
            }
            
            // For RUNNING status, check if we have async step state
            if (status == WorkflowInstance.WorkflowStatus.RUNNING && 
                instance.getAsyncStepStates() != null && 
                !instance.getAsyncStepStates().isEmpty()) {
                // Workflow has async steps - this is also a terminal state for initial response
                return instance;
            }
            
            // Small sleep to avoid busy waiting
            Thread.sleep(WAIT_INTERVAL_MS);
        }
        
        // Timeout reached
        throw new IllegalStateException("Workflow execution timeout after " + 
            (MAX_WAIT_ITERATIONS * WAIT_INTERVAL_MS / 1000) + " seconds for runId: " + runId);
    }
    
    private WorkflowMetadata createWorkflowMetadata(WorkflowGraph<?, ?> graph) {
        return new WorkflowMetadata(
                graph.id(),
                graph.version(),
                null, // No hardcoded description
                graph.inputType(),
                graph.outputType()
        );
    }
    
    private ChatResponse createChatResponseFromWorkflowState(
            String chatId,
            String userId,
            Language language,
            String workflowId,
            WorkflowInstance instance) {
        
        Map<String, String> responseProperties = new HashMap<>();
        AIFunctionSchema nextSchema = null;
        boolean completed = true;
        Integer percentComplete = 100;
        String responseId = UUID.randomUUID().toString(); // Default response ID
        
        switch (instance.getStatus()) {
            case SUSPENDED:
                SuspensionData suspensionData = instance.getSuspensionData();
                if (suspensionData != null) {
                    responseId = suspensionData.messageId();
                    
                    // Check if this is an async suspension by looking in AsyncStepStateRepository
                    Optional<AsyncStepState> asyncStateOpt = memoryService.getAsyncStepState(responseId);
                    if (asyncStateOpt.isPresent()) {
                        // This is an async step
                        AsyncStepState asyncState = asyncStateOpt.get();
                        
                        // Mark as not completed for async to enable polling
                        completed = false;
                        percentComplete = asyncState.getPercentComplete();
                        
                        // Extract properties from initial data
                        Object immediateData = asyncState.getInitialData();
                        if (immediateData != null) {
                            responseProperties = extractPropertiesFromData(immediateData);
                        }
                        
                        // Add status info
                        responseProperties.put("status", asyncState.getStatusMessage());
                        responseProperties.put("progressPercent", String.valueOf(asyncState.getPercentComplete()));
                    } else {
                        // Regular suspension (not async)
                        // Extract properties from promptToUser
                        Object promptToUser = suspensionData.promptToUser();
                        responseProperties = extractPropertiesFromData(promptToUser);
                        
                        // Generate schema for expected input
                        Class<?> nextInputClass = suspensionData.nextInputClass();
                        if (nextInputClass != null) {
                            nextSchema = schemaProvider.generateSchema(nextInputClass);
                        }
                    }
                }
                break;
                
            case COMPLETED:
                // Workflow completed
                // Get the last step result from context
                List<WorkflowInstance.StepExecutionRecord> history = instance.getExecutionHistory();
                if (!history.isEmpty()) {
                    WorkflowInstance.StepExecutionRecord lastStep = history.get(history.size() - 1);
                    Object finalResult = lastStep.getOutput();
                    responseProperties = extractPropertiesFromData(finalResult);
                }
                break;
                
            case FAILED:
                // Workflow failed
                WorkflowInstance.ErrorInfo errorInfo = instance.getErrorInfo();
                if (errorInfo != null) {
                    responseProperties.put("error", errorInfo.errorMessage());
                } else {
                    responseProperties.put("error", "Unknown error");
                }
                break;
                
            case RUNNING:
                // Running with async steps
                if (instance.getAsyncStepStates() != null && !instance.getAsyncStepStates().isEmpty()) {
                    completed = false;
                    percentComplete = 25;
                    
                    // Get first async step state for immediate data
                    var asyncState = instance.getAsyncStepStates().values().iterator().next();
                    // Use async state's messageId as response ID
                    responseId = asyncState.getMessageId();
                    
                    Object immediateData = asyncState.getInitialData();
                    if (immediateData != null) {
                        responseProperties = extractPropertiesFromData(immediateData);
                    }
                }
                break;
        }
        
        ChatResponse response = new ChatResponse(
                responseId,
                chatId,
                workflowId,
                language != null ? language : Language.GENERAL,
                completed,
                percentComplete,
                userId,
                responseProperties
        );
        
        // Set next schema if available
        if (nextSchema != null) {
            response.setNextSchemaAsSchema(nextSchema);
        }
        
        return response;
    }
    
    private Map<String, String> extractPropertiesFromData(Object data) {
        if (data == null) {
            return new HashMap<>();
        }

        // If data is already a properties map from a workflow response object
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            // Check if this is a response object with properties
            Object propsObj = map.get("properties");
            if (propsObj instanceof List) {
                // Handle DataProperty list
                Map<String, String> properties = new HashMap<>();
                List<?> propsList = (List<?>) propsObj;
                for (Object prop : propsList) {
                    if (prop instanceof DataProperty dp) {
                        properties.put(dp.getName(), dp.getValue());
                    }
                }
                return properties;
            } else {
                // Direct property map
                Map<String, String> properties = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        properties.put(k.toString(), v.toString());
                    }
                });
                return properties;
            }
        } else {
            // Try to extract properties using schema provider
            return schemaProvider.convertToMap(data);
        }
    }
    
    private ChatResponse createErrorResponse(ChatRequest request, Exception e) {
        Map<String, String> errorProps = new HashMap<>();
        errorProps.put("error", e.getMessage());

        return new ChatResponse(
                UUID.randomUUID().toString(),
                request.getChatId(),
                request.getWorkflowId(),
                request.getLanguage() != null ? request.getLanguage() : Language.GENERAL,
                true,
                100,
                request.getUserId(),
                errorProps
        );
    }
    
    private StepMetadata convertToStepMetadata(StepNode step) {
        AIFunctionSchema inputSchema = null;
        AIFunctionSchema outputSchema = null;

        // Get input type from the step executor
        if (step.executor() != null && step.executor().getInputType() != null
                && step.executor().getInputType() != void.class) {
            inputSchema = schemaProvider.generateSchema(step.executor().getInputType());
        }

        // Get output type from the step executor
        if (step.executor() != null && step.executor().getOutputType() != null
                && step.executor().getOutputType() != void.class) {
            outputSchema = schemaProvider.generateSchema(step.executor().getOutputType());
        }

        return new StepMetadata(
                step.id(),
                step.description(),
                step.isAsync(),
                inputSchema,
                outputSchema
        );
    }
}