package ai.driftkit.workflow.engine.spring.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.workflow.engine.chat.ChatContextHelper;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.SuspensionData;
import ai.driftkit.workflow.engine.chat.ChatDomain.*;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.chat.converter.ChatMessageTaskConverter;
import ai.driftkit.workflow.engine.domain.StepMetadata;
import ai.driftkit.workflow.engine.domain.WorkflowDetails;
import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowAnalyzer;
import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.graph.StepNode;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * Service for workflow management and chat integration.
 * Based on ChatWorkflowService from driftkit-chat-assistant-framework
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowEngine engine;
    private final SchemaProvider schemaProvider;

    // In-memory storage for demo - in production use proper repositories
    private final Map<String, ChatSession> chatSessions = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> chatHistories = new ConcurrentHashMap<>();
    private final Map<String, ChatResponse> asyncResponses = new ConcurrentHashMap<>();

    // ========== Chat Management ==========

    /**
     * Get or create a chat session.
     */
    public ChatSession getOrCreateChatSession(String chatId, String userId, String initialMessage) {
        return chatSessions.computeIfAbsent(chatId, id -> {
            String name = StringUtils.isEmpty(initialMessage)
                    ? "New Chat"
                    : StringUtils.abbreviate(initialMessage, 50);
            return ChatSession.create(id, userId, name);
        });
    }

    /**
     * Get a chat session by ID.
     */
    public Optional<ChatSession> getChatSession(String chatId) {
        return Optional.ofNullable(chatSessions.get(chatId));
    }

    /**
     * Create a new chat session.
     */
    public ChatSession createChatSession(String userId, String name) {
        String chatId = UUID.randomUUID().toString();
        ChatSession session = ChatSession.create(chatId, userId, name);
        chatSessions.put(chatId, session);
        return session;
    }

    /**
     * Archive a chat session.
     */
    public void archiveChatSession(String chatId) {
        ChatSession session = chatSessions.get(chatId);
        if (session != null) {
            chatSessions.put(chatId, session.archive());
        }
    }

    /**
     * List chats for a specific user.
     */
    public Page<ChatSession> listChatsForUser(String userId, Pageable pageable) {
        if (StringUtils.isEmpty(userId)) {
            return Page.empty(pageable);
        }

        List<ChatSession> userChats = chatSessions.values().stream()
                .filter(session -> userId.equals(session.getUserId()))
                .filter(session -> !session.isArchived())
                .sorted((a, b) -> Long.compare(b.getLastMessageTime(), a.getLastMessageTime()))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), userChats.size());

        if (start >= userChats.size()) {
            return Page.empty(pageable);
        }

        List<ChatSession> pageContent = userChats.subList(start, end);
        return new PageImpl<>(pageContent, pageable, userChats.size());
    }

    // ========== Chat Processing ==========

    /**
     * Process a chat request through the workflow engine.
     * Returns proper ChatResponse as defined in ChatDomain.
     */
    public ChatResponse processChatRequest(ChatRequest request) {
        try {
            // Store the request in chat history
            updateChatHistory(request.getChatId(), request);

            // Determine workflow to use
            String workflowId = determineWorkflow(request);
            if (workflowId == null) {
                throw new IllegalArgumentException("No workflow specified and no default workflow configured");
            }

            // Execute workflow with the chat request
            var execution = engine.execute(workflowId, request);
            String runId = execution.getRunId();
            
            // Wait for workflow to reach a terminal state (Suspend, Async, or Finish)
            WorkflowInstance instance;
            while (true) {
                Optional<WorkflowInstance> instanceOpt = engine.getWorkflowInstance(runId);
                if (!instanceOpt.isPresent()) {
                    throw new IllegalStateException("Workflow instance not found: " + runId);
                }
                
                instance = instanceOpt.get();
                WorkflowInstance.WorkflowStatus status = instance.getStatus();
                
                log.debug("Workflow {} status: {}, currentStep: {}", runId, status, instance.getCurrentStepId());
                
                // Break when we reach a terminal state
                if (status == WorkflowInstance.WorkflowStatus.SUSPENDED ||
                    status == WorkflowInstance.WorkflowStatus.COMPLETED ||
                    status == WorkflowInstance.WorkflowStatus.FAILED) {
                    break;
                }
                
                // For RUNNING status, check if we have async step state
                if (status == WorkflowInstance.WorkflowStatus.RUNNING && instance.getAsyncStepStates() != null && !instance.getAsyncStepStates().isEmpty()) {
                    // Workflow has async steps - this is also a terminal state for initial response
                    break;
                }
                
                // Small sleep to avoid busy waiting
                Thread.sleep(10);
            }

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
            updateChatHistory(request.getChatId(), response);

            return response;

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            // Return error response in proper ChatResponse format
            return createErrorResponse(request, e);
        }
    }
    
    /**
     * Create ChatResponse from workflow state.
     */
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
        
        switch (instance.getStatus()) {
            case SUSPENDED:
                // Workflow is waiting for user input
                SuspensionData suspensionData = instance.getSuspensionData();
                if (suspensionData != null) {
                    // Extract properties from promptToUser
                    Object promptToUser = suspensionData.promptToUser();
                    responseProperties = extractPropertiesFromData(promptToUser);
                    
                    // Generate schema for expected input
                    Class<?> nextInputClass = suspensionData.nextInputClass();
                    if (nextInputClass != null) {
                        nextSchema = schemaProvider.generateSchema(nextInputClass);
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
                    Object immediateData = asyncState.getInitialData();
                    if (immediateData != null) {
                        responseProperties = extractPropertiesFromData(immediateData);
                    }
                }
                break;
        }
        
        String responseId = UUID.randomUUID().toString();
        
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

    /**
     * Get a chat response by ID (for async polling).
     */
    public Optional<ChatResponse> getChatResponse(String responseId) {
        return Optional.ofNullable(asyncResponses.get(responseId));
    }

    /**
     * Get chat history.
     */
    public Page<ChatMessage> getChatHistory(String chatId, Pageable pageable, boolean includeContext, boolean includeSchema) {
        List<ChatMessage> history = chatHistories.getOrDefault(chatId, new ArrayList<>());

        if (!includeContext) {
            history = history.stream()
                    .filter(msg -> msg.getType() != MessageType.CONTEXT)
                    .toList();
        }

        // Sort by timestamp descending
        history = history.stream()
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), history.size());

        if (start >= history.size()) {
            return Page.empty(pageable);
        }

        List<ChatMessage> pageContent = history.subList(start, end);
        return new PageImpl<>(pageContent, pageable, history.size());
    }

    /**
     * Convert message to tasks for frontend display.
     */
    public List<ChatMessageTask> convertMessageToTasks(ChatMessage message) {
        // Use ChatMessageTaskConverter for proper conversion
        return ChatMessageTaskConverter.convert(message);
    }

    // ========== Workflow Management ==========

    /**
     * List all available workflows.
     */
    public List<WorkflowMetadata> listWorkflows() {
        // Get workflows from engine registry
        return engine.getRegisteredWorkflows().stream()
                .map(workflowId -> {
                    var graph = engine.getWorkflowGraph(workflowId);
                    if (graph == null) {
                        return null;
                    }
                    return new WorkflowMetadata(
                            workflowId,
                            graph.version(),
                            "Workflow " + workflowId,
                            graph.inputType(),
                            graph.outputType()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Get detailed information about a workflow.
     */
    public WorkflowDetails getWorkflowDetails(String workflowId) {
        // Get workflow graph from engine
        var graph = engine.getWorkflowGraph(workflowId);
        if (graph == null) {
            return null;
        }

        // Convert steps to metadata
        List<StepMetadata> steps = graph.nodes().values().stream()
                .map(this::convertToStepMetadata)
                .toList();

        AIFunctionSchema initialSchema = getInitialSchema(workflowId);

        WorkflowMetadata metadata = new WorkflowMetadata(
                graph.id(),
                graph.version(),
                "Workflow " + graph.id(),
                graph.inputType(),
                graph.outputType()
        );

        return new WorkflowDetails(
                metadata,
                steps,
                graph.initialStepId(),
                initialSchema
        );
    }

    /**
     * Get all schemas for a workflow.
     */
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


    /**
     * Get the initial schema for a workflow.
     */
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

    /**
     * Get workflow state by run ID.
     */
    public Optional<WorkflowInstance> getWorkflowState(String runId) {
        // This would query the workflow state repository
        return Optional.empty();
    }

    // ========== Private Helper Methods ==========

    private String determineWorkflow(ChatRequest request) {
        // Return the workflow ID from request, or null if not specified
        // Let the calling code handle the default workflow selection
        return request.getWorkflowId();
    }

    private WorkflowContext createContextFromChatRequest(ChatRequest request) {
        WorkflowContext context = ChatContextHelper.initChatContext(
                request.getChatId(),
                request.getUserId(),
                request
        );

        // Add language
        context.setStepOutput("language", request.getLanguage());

        // Add properties as context
        if (MapUtils.isNotEmpty(request.getPropertiesMap())) {
            // Convert Map<String, String> to Map<String, Object>
            Map<String, Object> properties = new HashMap<>(request.getPropertiesMap());
            context.setStepOutputs(properties);
        }

        return context;
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

    private void updateChatHistory(String chatId, ChatMessage message) {
        List<ChatMessage> history = chatHistories.computeIfAbsent(chatId, k -> new ArrayList<>());
        history.add(message);

        // Update session last message time
        ChatSession session = chatSessions.get(chatId);
        if (session != null) {
            chatSessions.put(chatId, session.withLastMessageTime(message.getTimestamp()));
        }
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