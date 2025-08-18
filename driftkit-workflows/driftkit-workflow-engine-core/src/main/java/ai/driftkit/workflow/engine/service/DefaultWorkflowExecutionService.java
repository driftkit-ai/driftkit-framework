package ai.driftkit.workflow.engine.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatMessage.DataProperty;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.common.service.ChatStore;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.chat.ChatResponseExtensions;
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
@RequiredArgsConstructor(staticName = "of")
public class DefaultWorkflowExecutionService implements WorkflowExecutionService {
    
    private static final int MAX_WAIT_ITERATIONS = 10000; // 100 seconds max wait
    private static final long WAIT_INTERVAL_MS = 10;
    
    private final WorkflowEngine engine;
    private final SchemaProvider schemaProvider;
    private final MemoryManagementService memoryService;
    private final ChatStore chatStore;
    
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
            chatStore.add(request);

            // Determine workflow to use
            String workflowId = request.getWorkflowId();
            if (workflowId == null) {
                throw new IllegalArgumentException("No workflow specified in request");
            }

            // Execute workflow with the chat request using chatId as instanceId
            var execution = engine.execute(workflowId, request, request.getChatId());
            String runId = execution.getRunId();
            
            // Notify event publisher if available
            if (eventPublisher != null) {
                eventPublisher.publishWorkflowStarted(runId, workflowId);
            }
            
            // Wait for workflow to reach a terminal state
            WorkflowInstance instance = waitForTerminalState(runId);
            
            log.info("Workflow {} reached state: {}", 
                runId, instance.getStatus());
            
            // Create ChatResponse based on workflow state
            ChatResponse response = createChatResponseFromWorkflowState(
                    request.getChatId(),
                    request.getUserId(),
                    request.getLanguage(),
                    workflowId,
                    instance
            );

            // Update chat history with response
            chatStore.add(response);
            
            // Update session last message time
            memoryService.updateSessionLastMessageTime(response.getChatId(), response.getTimestamp());

            return response;

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            // Return error response in proper ChatResponse format
            ChatResponse errorResponse = createErrorResponse(request, e);
            chatStore.add(errorResponse);
            memoryService.updateSessionLastMessageTime(errorResponse.getChatId(), errorResponse.getTimestamp());
            return errorResponse;
        }
    }
    
    @Override
    public ChatResponse resumeChat(String messageId, ChatRequest request) {
        try {
            // Store the resume request in chat history
            chatStore.add(request);
            
            // Find the original message in chat history
            ChatMessage originalMessage = chatStore.getById(messageId);
            if (originalMessage == null || !(originalMessage instanceof ChatResponse)) {
                throw new IllegalArgumentException("No chat response found for messageId: " + messageId);
            }
            
            ChatResponse originalResponse = (ChatResponse) originalMessage;
            
            // Use chatId as instanceId
            String instanceId = originalResponse.getChatId();
            
            // Get the workflow instance
            Optional<WorkflowInstance> instanceOpt = engine.getWorkflowInstance(instanceId);
            if (!instanceOpt.isPresent()) {
                throw new IllegalArgumentException("No workflow instance found for instanceId: " + instanceId);
            }
            
            WorkflowInstance instance = instanceOpt.get();
            if (instance.getStatus() != WorkflowInstance.WorkflowStatus.SUSPENDED) {
                throw new IllegalStateException("Workflow is not in suspended state: " + instance.getStatus());
            }
            
            // Get expected input type from schema registry
            Object resumeInput;
            String schemaName = request.getRequestSchemaName();
            if (schemaName != null) {
                Class<?> expectedInputClass = schemaProvider.getSchemaClass(schemaName);
                if (expectedInputClass != null) {
                    // Convert properties map to expected type
                    resumeInput = schemaProvider.convertFromMap(
                        request.getPropertiesMap(), 
                        expectedInputClass
                    );
                } else {
                    log.warn("Schema not found in registry: {}", schemaName);
                    resumeInput = request;
                }
            } else {
                // No schema specified, use request as-is
                resumeInput = request;
            }
            
            // Resume the workflow with converted input
            var execution = engine.resume(instance.getInstanceId(), resumeInput);
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
            chatStore.add(response);
            memoryService.updateSessionLastMessageTime(response.getChatId(), response.getTimestamp());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error resuming chat for messageId: {}", messageId, e);
            ChatResponse errorResponse = createErrorResponse(request, e);
            chatStore.add(errorResponse);
            memoryService.updateSessionLastMessageTime(errorResponse.getChatId(), errorResponse.getTimestamp());
            return errorResponse;
        }
    }
    
    @Override
    public Optional<ChatResponse> getAsyncStatus(String messageId) {
        // Get async state
        Optional<AsyncStepState> asyncStateOpt = memoryService.getAsyncStepState(messageId);
        if (asyncStateOpt.isEmpty()) {
            return Optional.empty();
        }
        
        AsyncStepState asyncState = asyncStateOpt.get();
        
        // Get original response from history
        ChatMessage originalMessage = chatStore.getById(messageId);
        if (originalMessage == null || !(originalMessage instanceof ChatResponse)) {
            return Optional.empty();
        }
        
        ChatResponse original = (ChatResponse) originalMessage;
        
        // Create updated response based on current async state
        ChatResponse response = new ChatResponse(
            messageId,
            original.getChatId(),
            original.getWorkflowId(),
            original.getLanguage(),
            asyncState.isCompleted(),
            asyncState.getPercentComplete(),
            original.getUserId(),
            new HashMap<>()
        );
        
        // Update properties based on state
        if (asyncState.isCompleted() && asyncState.getResultData() != null) {
            // Use final result data
            response.setPropertiesMap(extractPropertiesFromData(asyncState.getResultData()));
        } else {
            // Use initial data with updated progress
            response.setPropertiesMap(extractPropertiesFromData(asyncState.getInitialData()));
        }

        return Optional.of(response);
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
        // For now, return all messages - pagination can be implemented later in ChatStore
        List<ChatMessage> messages = chatStore.getAll(chatId);
        
        // Create PageResult manually
        int start = pageRequest.getPageNumber() * pageRequest.getPageSize();
        int end = Math.min(start + pageRequest.getPageSize(), messages.size());
        List<ChatMessage> pageContent = start < messages.size() ? 
            messages.subList(start, end) : Collections.emptyList();
            
        return new PageResult<>(
            pageContent,
            pageRequest.getPageNumber(),
            pageRequest.getPageSize(),
            messages.size()
        );
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
            
            // For RUNNING status, check if we have suspension data with async flag
            if (status == WorkflowInstance.WorkflowStatus.RUNNING) {
                // Check if we have suspension data
                Optional<SuspensionData> suspensionDataOpt = memoryService.getSuspensionData(instance.getInstanceId());
                if (suspensionDataOpt.isPresent()) {
                    SuspensionData suspensionData = suspensionDataOpt.get();
                    // Check if this is an async suspension by looking for async step state
                    Optional<AsyncStepState> asyncStateOpt = memoryService.getAsyncStepState(suspensionData.messageId());
                    if (asyncStateOpt.isPresent()) {
                        // Workflow has async steps - this is also a terminal state for initial response
                        return instance;
                    }
                }
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
    
    /**
     * Creates a chat response from workflow state.
     */
    private ChatResponse createChatResponseFromWorkflowState(
            String chatId,
            String userId,
            Language language,
            String workflowId,
            WorkflowInstance instance) {
        
        switch (instance.getStatus()) {
            case SUSPENDED:
                SuspensionData suspensionData = memoryService.getSuspensionData(instance.getInstanceId()).orElse(null);
                if (suspensionData != null) {
                    // Check if this is an async suspension
                    Optional<AsyncStepState> asyncStateOpt = memoryService.getAsyncStepState(suspensionData.messageId());
                    if (asyncStateOpt.isPresent()) {
                        // Async suspension
                        AsyncStepState asyncState = asyncStateOpt.get();
                        return createAsyncResponse(
                            chatId, userId, language, workflowId,
                            asyncState.getInitialData(),
                            asyncState.getMessageId(),
                            asyncState.getPercentComplete(),
                            asyncState.getStatusMessage()
                        );
                    } else {
                        // Regular suspension
                        AIFunctionSchema nextSchema = null;
                        if (suspensionData.nextInputClass() != null) {
                            nextSchema = schemaProvider.generateSchema(suspensionData.nextInputClass());
                        }
                        return createSuspendResponse(
                            chatId, userId, language, workflowId,
                            suspensionData.promptToUser(),
                            nextSchema,
                            suspensionData.messageId()
                        );
                    }
                }
                break;
                
            case COMPLETED:
                // Get the last step result
                List<WorkflowInstance.StepExecutionRecord> history = instance.getExecutionHistory();
                Object finalResult = null;
                if (!history.isEmpty()) {
                    WorkflowInstance.StepExecutionRecord lastStep = history.get(history.size() - 1);
                    finalResult = lastStep.getOutput();
                }
                return createCompletedResponse(chatId, userId, language, workflowId, finalResult);
                
            case FAILED:
                // Get error message
                String errorMessage = "Unknown error";
                WorkflowInstance.ErrorInfo errorInfo = instance.getErrorInfo();
                if (errorInfo != null) {
                    errorMessage = errorInfo.errorMessage();
                }
                return createErrorResponse(chatId, userId, language, workflowId, errorMessage);
                
            case RUNNING:
                // Should not happen in normal flow
                log.warn("Workflow {} is still RUNNING when creating response", instance.getInstanceId());
                break;
        }
        
        // Fallback response
        return createErrorResponse(chatId, userId, language, workflowId, 
            "Unexpected workflow state: " + instance.getStatus());
    }
    
    /**
     * Creates a suspension response.
     */
    private ChatResponse createSuspendResponse(String chatId, String userId, Language language, 
                                              String workflowId, Object promptData, 
                                              AIFunctionSchema nextSchema, String messageId) {
        Map<String, String> properties = extractPropertiesFromData(promptData);
        
        ChatResponse response = new ChatResponse(
            messageId != null ? messageId : UUID.randomUUID().toString(),
            chatId,
            workflowId,
            language != null ? language : Language.GENERAL,
            true,  // Suspended responses are "completed" from UI perspective
            100,
            userId,
            properties
        );
        
        if (nextSchema != null) {
            ChatResponseExtensions.setNextSchemaAsSchema(response, nextSchema);
        }
        
        return response;
    }
    
    /**
     * Creates an async response.
     */
    private ChatResponse createAsyncResponse(String chatId, String userId, Language language,
                                           String workflowId, Object immediateData,
                                           String messageId, int percentComplete,
                                           String statusMessage) {
        Map<String, String> properties = extractPropertiesFromData(immediateData);
        if (statusMessage != null) {
            properties.put("status", statusMessage);
        }
        properties.put("progressPercent", String.valueOf(percentComplete));
        
        return new ChatResponse(
            messageId != null ? messageId : UUID.randomUUID().toString(),
            chatId,
            workflowId,
            language != null ? language : Language.GENERAL,
            false,  // NOT completed
            percentComplete,
            userId,
            properties
        );
    }
    
    /**
     * Creates a completed response.
     */
    private ChatResponse createCompletedResponse(String chatId, String userId, Language language,
                                               String workflowId, Object result) {
        Map<String, String> properties = extractPropertiesFromData(result);
        
        return new ChatResponse(
            UUID.randomUUID().toString(),
            chatId,
            workflowId,
            language != null ? language : Language.GENERAL,
            true,
            100,
            userId,
            properties
        );
    }
    
    /**
     * Creates an error response.
     */
    private ChatResponse createErrorResponse(String chatId, String userId, Language language,
                                           String workflowId, String errorMessage) {
        Map<String, String> properties = new HashMap<>();
        properties.put("error", errorMessage != null ? errorMessage : "Unknown error");
        
        return new ChatResponse(
            UUID.randomUUID().toString(),
            chatId,
            workflowId,
            language != null ? language : Language.GENERAL,
            true,
            100,
            userId,
            properties
        );
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
            } else if (propsObj instanceof Map) {
                // Direct property map in "properties" field
                Map<String, String> properties = new HashMap<>();
                Map<?, ?> propsMap = (Map<?, ?>) propsObj;
                propsMap.forEach((k, v) -> {
                    if (k != null && v != null) {
                        properties.put(k.toString(), v.toString());
                    }
                });
                return properties;
            } else {
                // No "properties" field, treat as plain map
                Map<String, String> properties = new HashMap<>();
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        properties.put(k.toString(), v.toString());
                    }
                });
                return properties;
            }
        } else {
            // Not a map, use schema provider to extract properties
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