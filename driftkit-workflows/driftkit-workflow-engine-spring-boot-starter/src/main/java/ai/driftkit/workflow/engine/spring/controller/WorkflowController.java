package ai.driftkit.workflow.engine.spring.controller;

import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.async.ProgressTracker.Progress;
import ai.driftkit.workflow.engine.core.StepResult.Finish;
import ai.driftkit.workflow.engine.core.WorkflowContext.Keys;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.core.StepResult;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.WorkflowEvent;
import ai.driftkit.workflow.engine.persistence.WorkflowInstance;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.schema.SchemaProvider;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import ai.driftkit.workflow.engine.chat.ChatDomain.*;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.chat.converter.ChatMessageTaskConverter;
import ai.driftkit.workflow.engine.domain.StepMetadata;
import ai.driftkit.workflow.engine.domain.WorkflowDetails;
import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST controller for workflow execution and management.
 * Based on AssistantController from driftkit-chat-assistant-framework
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Validated
public class WorkflowController {
    
    private final WorkflowEngine engine;
    private final SchemaProvider schemaProvider;
    private final ProgressTracker progressTracker;
    private final WorkflowService workflowService;
    
    // Enums
    
    public enum WorkflowStatus {
        PENDING,
        RUNNING,
        SUSPENDED,
        COMPLETED,
        FAILED,
        CANCELLED,
        ASYNC
    }
    
    public enum StepStatus {
        CONTINUE,
        SUSPEND,
        FINISH,
        FAIL,
        ASYNC
    }
    
    // ========== Chat-specific endpoints (from AssistantController) ==========
    
    /**
     * Process a chat request through a workflow.
     * Equivalent to AssistantController.chat()
     * 
     * @param request The chat request
     * @param userId Optional user ID
     * @return Chat response with tasks
     */
    @PostMapping("/chat")
    public ChatResponseWithTasks chat(
            @Valid @RequestBody ChatRequest request,
            @RequestParam(required = false) String userId
    ) {
        userId = decode(userId);
        log.info("Processing chat request for session: {}, user: {}", request.getChatId(), userId);
        
        try {
            // Set user ID if not provided
            if (!StringUtils.hasText(request.getUserId())) {
                if (StringUtils.hasText(userId)) {
                    request.setUserId(userId);
                } else {
                    Map<String, String> propsMap = request.getPropertiesMap();
                    userId = propsMap.getOrDefault("userId", "anonymous");
                    request.setUserId(userId);
                }
            }
            
            // Ensure chat session exists
            workflowService.getOrCreateChatSession(request.getChatId(), userId, request.getMessage());
            
            // Process the chat request through workflow
            ChatResponse response = workflowService.processChatRequest(request);
            
            // Convert to response with tasks
            List<ChatMessageTask> requestTasks = workflowService.convertMessageToTasks(request);
            List<ChatMessageTask> responseTasks = workflowService.convertMessageToTasks(response);
            
            return new ChatResponseWithTasks(response, requestTasks, responseTasks);
            
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error processing chat request: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Get chat response by ID (for async polling).
     * Equivalent to AssistantController.getChatResponse()
     * 
     * @param responseId The response ID
     * @param userId Optional user ID for verification
     * @return Chat response with tasks
     */
    @GetMapping("/chat/response/{responseId}")
    public ChatResponseWithTasks getChatResponse(
            @PathVariable String responseId,
            @RequestParam(required = false) String userId
    ) {
        userId = decode(userId);
        log.info("Getting chat response for ID: {}, user: {}", responseId, userId);
        
        try {
            Optional<ChatResponse> response = workflowService.getChatResponse(responseId);
            
            // Verify user access if userId provided
            if (response.isPresent() && StringUtils.hasText(userId)) {
                ChatResponse chatResponse = response.get();
                String responseUserId = chatResponse.getUserId();
                
                if (StringUtils.hasText(responseUserId) && !responseUserId.equals(userId)) {
                    log.warn("User {} attempted to access response {} owned by {}",
                        userId, responseId, responseUserId);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        String.format("Forbidden for [%s] [%s]", userId, responseId));
                }
            }
            
            if (response.isEmpty()) {
                return new ChatResponseWithTasks();
            }
            
            List<ChatMessageTask> responseTasks = workflowService.convertMessageToTasks(response.get());
            return new ChatResponseWithTasks(response.get(), null, responseTasks);
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving chat response", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error retrieving chat response: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Get chat history with pagination.
     * Equivalent to AssistantController.history()
     * 
     * @param chatId The chat ID
     * @param userId Optional user ID
     * @param page Page number
     * @param limit Items per page
     * @param sort Sort direction
     * @param showSchema Whether to include schemas
     * @param context Whether to include context messages
     * @return List of chat message tasks
     */
    @GetMapping("/chat/history")
    public List<ChatMessageTask> getChatHistory(
            @RequestParam String chatId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "1000") int limit,
            @RequestParam(required = false, defaultValue = "asc") String sort,
            @RequestParam(required = false, defaultValue = "true") boolean showSchema,
            @RequestParam(required = false, defaultValue = "false") Boolean context
    ) {
        userId = decode(userId);
        log.info("Retrieving chat history for session: {}, user: {}, page: {}, limit: {}, sort: {}",
                chatId, userId, page, limit, sort);
        
        try {
            Pageable pageable = createPageable(page, limit, sort, "timestamp");
            
            // Verify user access
            verifyUserChatAccess(chatId, userId);
            
            // Get chat history
            Page<ChatMessage> historyPage = workflowService.getChatHistory(chatId, pageable, context, showSchema);
            
            if (historyPage.isEmpty()) {
                log.info("No history found for chat: {}", chatId);
                return new ArrayList<>();
            }
            
            return ChatMessageTaskConverter.convertAll(historyPage.getContent());
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving chat history", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error retrieving chat history: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * List available chats with pagination.
     * Equivalent to AssistantController.getChats()
     * 
     * @param userId User ID
     * @param page Page number
     * @param limit Items per page
     * @param sort Sort direction
     * @return Paginated chat list
     */
    @GetMapping("/chat/list")
    public Page<ChatInfo> listChats(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int limit,
            @RequestParam(required = false, defaultValue = "desc") String sort
    ) {
        userId = decode(userId);
        log.info("Listing chats for user: {}, page: {}, limit: {}, sort: {}", userId, page, limit, sort);
        
        try {
            Pageable pageable = createPageable(page, limit, sort, "lastMessageTime");
            
            if (!StringUtils.hasText(userId)) {
                return Page.empty(pageable);
            }
            
            Page<ChatSession> chatsPage = workflowService.listChatsForUser(userId, pageable);
            
            return chatsPage.map(session -> new ChatInfo(
                session.getChatId(),
                session.getLastMessageTime(),
                session.getDescription(),
                session.getUserId(),
                session.getName()
            ));
            
        } catch (Exception e) {
            log.error("Error listing chats", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error listing chats: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Create a new chat session.
     * Equivalent to AssistantController.createChat()
     * 
     * @param userId User ID
     * @param name Chat name
     * @return Created chat info
     */
    @PostMapping("/chat/create")
    public ChatInfo createChat(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String name
    ) {
        userId = decode(userId);
        userId = StringUtils.hasText(userId) ? userId : "anonymous";
        log.info("Creating new chat for user: {}", userId);
        
        try {
            ChatSession chat = workflowService.createChatSession(userId, name);
            
            return new ChatInfo(
                chat.getChatId(),
                chat.getLastMessageTime(),
                chat.getDescription(),
                chat.getUserId(),
                chat.getName()
            );
            
        } catch (Exception e) {
            log.error("Error creating new chat", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error creating new chat: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Archive a chat session.
     * Equivalent to AssistantController.archiveChat()
     * 
     * @param chatId Chat ID to archive
     * @param userId User ID for verification
     * @return Empty response
     */
    @PostMapping("/chat/{chatId}/archive")
    public ResponseEntity<Void> archiveChat(
            @PathVariable String chatId,
            @RequestParam(required = false) String userId
    ) {
        userId = decode(userId);
        log.info("Archiving chat: {}, user: {}", chatId, userId);
        
        try {
            verifyUserChatAccess(chatId, userId);
            workflowService.archiveChatSession(chatId);
            return ResponseEntity.ok().build();
            
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("Error archiving chat", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error archiving chat: " + e.getMessage(),
                e
            );
        }
    }
    
    // ========== Workflow-specific endpoints ==========
    
    /**
     * Execute a workflow with the given input.
     * 
     * @param workflowId The workflow ID
     * @param request The execution request
     * @return The workflow response
     */
    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<WorkflowResponse> execute(
            @PathVariable String workflowId,
            @RequestBody WorkflowExecutionRequest request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId
    ) {
        try {
            log.debug("Executing workflow: workflowId={}, sessionId={}", workflowId, sessionId);
            
            // Convert input data using schema provider if needed
            Object input = request.properties();
            if (request.inputClass() != null) {
                Class<?> inputClass = Class.forName(request.inputClass());
                input = schemaProvider.convertFromMap(request.properties(), inputClass);
            }
            
            // Execute workflow
            var execution = engine.execute(workflowId, input);
            
            // Check if async
            if (execution.isAsync()) {
                String taskId = progressTracker.generateTaskId();
                WorkflowEvent event = WorkflowEvent.asyncStarted(taskId, execution.getRunId());
                progressTracker.trackExecution(taskId, event);
                
                // Return immediate response with task ID
                return ResponseEntity.accepted()
                    .body(WorkflowResponse.async(execution.getRunId(), taskId));
            }
            
            // Get synchronous result
            Object result = execution.getResult();
            
            // Create WorkflowInstance for the response
            WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId(execution.getRunId())
                .workflowId(workflowId)
                .status(WorkflowInstance.WorkflowStatus.COMPLETED)
                .build();
                
            // Wrap result in a Finish StepResult for consistent response format
            StepResult<?> stepResult = new Finish<>(result);
            
            return ResponseEntity.ok(WorkflowResponse.from(instance, stepResult));
            
        } catch (Exception e) {
            log.error("Error executing workflow: workflowId={}", workflowId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(WorkflowResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Resume a suspended workflow with user input.
     * 
     * @param runId The workflow run ID
     * @param request The resume request with user input
     * @return The workflow response
     */
    @PostMapping("/{runId}/resume")
    public ResponseEntity<WorkflowResponse> resume(
            @PathVariable String runId,
            @RequestBody WorkflowResumeRequest request
    ) {
        try {
            log.debug("Resuming workflow: runId={}", runId);
            
            // Convert user input if schema provided
            Object userInput = request.getUserInput();
            if (request.inputClass() != null) {
                Class<?> inputClass = Class.forName(request.inputClass());
                userInput = schemaProvider.convertFromMap(request.properties(), inputClass);
            }
            
            // Resume workflow
            var execution = engine.resume(runId, userInput);
            
            // Get result
            Object result = execution.getResult();
            
            // Create WorkflowInstance for the response
            WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId(execution.getRunId())
                .workflowId(execution.getWorkflowId())
                .status(WorkflowInstance.WorkflowStatus.COMPLETED)
                .build();
                
            // Wrap result in a Finish StepResult for consistent response format
            StepResult<?> stepResult = new Finish<>(result);
                
            return ResponseEntity.ok(WorkflowResponse.from(instance, stepResult));
            
        } catch (Exception e) {
            log.error("Error resuming workflow: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(WorkflowResponse.error(e.getMessage()));
        }
    }
    
    /**
     * Get the status of a workflow execution.
     * 
     * @param runId The workflow run ID
     * @return The workflow status
     */
    @GetMapping("/{runId}/status")
    public ResponseEntity<WorkflowStatusResponse> getStatus(@PathVariable String runId) {
        try {
            // Check with progress tracker first for async tasks
            var progress = progressTracker.getExecution(runId)
                .flatMap(event -> progressTracker.getProgress(event.getAsyncTaskId()));
            
            if (progress.isPresent()) {
                return ResponseEntity.ok(WorkflowStatusResponse.fromProgress(progress.get()));
            }
            
            // Check workflow state
            var state = workflowService.getWorkflowState(runId);
            if (state.isPresent()) {
                return ResponseEntity.ok(WorkflowStatusResponse.fromState(state.get()));
            }
            
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Error getting workflow status: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get the current result of a workflow execution.
     * This returns the current state, including intermediate results for async/suspended workflows.
     * 
     * @param runId The workflow run ID
     * @return The current workflow result
     */
    @GetMapping("/{runId}/current")
    public ResponseEntity<WorkflowCurrentResultResponse> getCurrentResult(@PathVariable String runId) {
        try {
            Optional<WorkflowEvent> currentResult = engine.getCurrentResult(runId);
            
            if (currentResult.isPresent()) {
                WorkflowEvent event = currentResult.get();
                String message = event.getProperties() != null ? 
                    event.getProperties().get("message") : null;
                    
                Map<String, Object> data = event.getProperties() != null ? 
                    new HashMap<>(event.getProperties()) : null;
                    
                return ResponseEntity.ok(new WorkflowCurrentResultResponse(
                    runId,
                    event.isCompleted() ? WorkflowStatus.COMPLETED : WorkflowStatus.RUNNING,
                    event.getPercentComplete(),
                    message,
                    data,
                    event.isAsync()
                ));
            }
            
            // Fallback to checking workflow state
            var state = workflowService.getWorkflowState(runId);
            if (state.isPresent()) {
                WorkflowInstance instance = state.get();
                Object result = instance.getContext() != null 
                    ? instance.getContext().getStepOutputs().get(Keys.FINAL_RESULT)
                    : null;
                    
                return ResponseEntity.ok(new WorkflowCurrentResultResponse(
                    runId,
                    mapWorkflowStatus(instance.getStatus()),
                    instance.getStatus() == WorkflowInstance.WorkflowStatus.COMPLETED ? 100 : 0,
                    "Workflow " + instance.getStatus().toString().toLowerCase(),
                    result != null ? Map.of("result", result) : null,
                    false
                ));
            }
            
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Error getting current result: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Cancel an async operation for a workflow.
     * 
     * @param runId The workflow run ID
     * @return Success indicator
     */
    @PostMapping("/{runId}/cancel")
    public ResponseEntity<WorkflowCancelResponse> cancelAsyncOperation(@PathVariable String runId) {
        try {
            boolean cancelled = engine.cancelAsyncOperation(runId);
            
            if (cancelled) {
                return ResponseEntity.ok(new WorkflowCancelResponse(
                    runId,
                    true,
                    "Async operation cancelled successfully"
                ));
            } else {
                return ResponseEntity.ok(new WorkflowCancelResponse(
                    runId,
                    false,
                    "No active async operation found for this workflow"
                ));
            }
            
        } catch (Exception e) {
            log.error("Error cancelling async operation: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new WorkflowCancelResponse(
                    runId,
                    false,
                    "Error cancelling operation: " + e.getMessage()
                ));
        }
    }
    
    private WorkflowStatus mapWorkflowStatus(WorkflowInstance.WorkflowStatus status) {
        return switch (status) {
            case RUNNING -> WorkflowStatus.RUNNING;
            case SUSPENDED -> WorkflowStatus.SUSPENDED;
            case COMPLETED -> WorkflowStatus.COMPLETED;
            case FAILED -> WorkflowStatus.FAILED;
            case CANCELLED -> WorkflowStatus.CANCELLED;
        };
    }
    
    /**
     * Get all schemas for all workflows.
     * Equivalent to AssistantController.schemas()
     * 
     * @return Schema response with all available schemas
     */
    @GetMapping("/schemas")
    public SchemaResponse getAllSchemas() {
        log.info("Retrieving schemas for all workflows");
        
        try {
            Set<AIFunctionSchema> schemas = new HashSet<>();
            Map<String, String> messageIds = new HashMap<>();
            
            // Collect schemas from all registered workflows
            List<WorkflowMetadata> workflows = workflowService.listWorkflows();
            for (WorkflowMetadata workflow : workflows) {
                List<AIFunctionSchema> workflowSchemas = workflowService.getWorkflowSchemas(workflow.id());
                schemas.addAll(workflowSchemas);
            }
            
            return new SchemaResponse(new ArrayList<>(schemas), messageIds);
            
        } catch (Exception e) {
            log.error("Error retrieving schemas", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error retrieving schemas: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Get all schemas for a specific workflow.
     * 
     * @param workflowId The workflow ID
     * @return List of schemas for all steps
     */
    @GetMapping("/{workflowId}/schemas")
    public ResponseEntity<List<AIFunctionSchema>> getWorkflowSchemas(@PathVariable String workflowId) {
        try {
            List<AIFunctionSchema> schemas = workflowService.getWorkflowSchemas(workflowId);
            return ResponseEntity.ok(schemas);
        } catch (Exception e) {
            log.error("Error getting workflow schemas: workflowId={}", workflowId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get the initial schema for a workflow.
     * Equivalent to AssistantController.getFirstStepSchema()
     * 
     * @param workflowId The workflow ID
     * @return The initial input schema
     */
    @GetMapping("/{workflowId}/schema/initial")
    public ResponseEntity<FirstStepSchemaResponse> getInitialSchema(@PathVariable String workflowId) {
        log.info("Getting first step schema for workflow: {}", workflowId);
        
        try {
            WorkflowDetails details = workflowService.getWorkflowDetails(workflowId);
            if (details == null || details.steps().isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            StepMetadata firstStep = details.steps().get(0);
            List<AIFunctionSchema> schemas = firstStep.inputSchema() != null 
                ? List.of(firstStep.inputSchema()) 
                : Collections.emptyList();
            
            return ResponseEntity.ok(new FirstStepSchemaResponse(workflowId, firstStep.id(), schemas));
            
        } catch (Exception e) {
            log.error("Error getting initial schema: workflowId={}", workflowId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * List all available workflows
     * 
     * @return List of workflow metadata
     */
    @GetMapping
    public ResponseEntity<List<WorkflowMetadata>> listWorkflows() {
        try {
            List<WorkflowMetadata> workflows = workflowService.listWorkflows();
            return ResponseEntity.ok(workflows);
        } catch (Exception e) {
            log.error("Error listing workflows", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get detailed information about a workflow
     * 
     * @param workflowId The workflow ID
     * @return Workflow details
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<WorkflowDetails> getWorkflowDetails(@PathVariable String workflowId) {
        try {
            WorkflowDetails details = workflowService.getWorkflowDetails(workflowId);
            if (details != null) {
                return ResponseEntity.ok(details);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting workflow details: workflowId={}", workflowId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // ========== Helper methods ==========
    
    private static String decode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
    
    private Pageable createPageable(int page, int limit, String sort, String sortBy) {
        Direction sortDirection = "asc".equalsIgnoreCase(sort)
            ? Direction.ASC
            : Direction.DESC;
        
        return PageRequest.of(page, limit, Sort.by(sortDirection, sortBy));
    }
    
    private void verifyUserChatAccess(String chatId, String userId) {
        if (StringUtils.hasText(userId)) {
            Optional<ChatSession> chatOpt = workflowService.getChatSession(chatId);
            if (chatOpt.isPresent() && !userId.equals(chatOpt.get().getUserId())) {
                log.warn("User {} attempted to access chat {} owned by {}", 
                    userId, chatId, chatOpt.get().getUserId());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized to access this chat");
            }
        }
    }
    
    
    // ========== Request/Response DTOs ==========
    
    // Using ChatDomain classes directly
    
    /**
     * Wrapper class for ChatResponse with MessageTasks
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponseWithTasks {
        private ChatResponse originalResponse;
        private List<ChatMessageTask> request;
        private List<ChatMessageTask> response;
    }
    
    /**
     * Chat info summary
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatInfo {
        private String chatId;
        private Long lastMessageTime;
        private String lastMessage;
        private String userId;
        private String name;
    }
    
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaResponse {
        private List<AIFunctionSchema> schemas;
        private Map<String, String> messageIds;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FirstStepSchemaResponse {
        private String workflowId;
        private String stepId;
        private List<AIFunctionSchema> schemas;
    }
    
    // Workflow-specific DTOs
    
    record WorkflowExecutionRequest(
        Map<String, String> properties,
        String inputClass
    ) {
    }
    
    record WorkflowResumeRequest(
        Map<String, Object> userInput,
        Map<String, String> properties,
        String inputClass
    ) {
        public Object getUserInput() {
            return userInput != null ? userInput : properties;
        }
    }
    
    record WorkflowResponse(
        String runId,
        StepStatus status,
        Object result,
        String error,
        boolean async,
        String asyncTaskId,
        AIFunctionSchema nextInputSchema,
        List<AIFunctionSchema> possibleSchemas
    ) {
        public static WorkflowResponse from(WorkflowInstance instance, StepResult<?> result) {
            StepStatus status;
            Object data = null;
            String error = null;
            
            if (result instanceof StepResult.Continue<?> cont) {
                status = StepStatus.CONTINUE;
                data = cont.data();
            } else if (result instanceof StepResult.Suspend<?> susp) {
                status = StepStatus.SUSPEND;
                data = Map.of("suspensionId", instance.getInstanceId() + "_" + instance.getCurrentStepId());
            } else if (result instanceof StepResult.Finish<?> fin) {
                status = StepStatus.FINISH;
                data = fin.result();
            } else if (result instanceof StepResult.Fail<?> fail) {
                status = StepStatus.FAIL;
                error = fail.error().getMessage();
            } else {
                status = StepStatus.CONTINUE;
            }
            
            return new WorkflowResponse(
                instance.getInstanceId(),
                status,
                data,
                error,
                false,
                null,
                null,
                null
            );
        }
        
        public static WorkflowResponse async(String runId, String taskId) {
            return new WorkflowResponse(
                runId,
                StepStatus.ASYNC,
                null,
                null,
                true,
                taskId,
                null,
                null
            );
        }
        
        public static WorkflowResponse error(String error) {
            return new WorkflowResponse(
                null,
                StepStatus.FAIL,
                null,
                error,
                false,
                null,
                null,
                null
            );
        }
    }
    
    record WorkflowStatusResponse(
        WorkflowStatus status,
        int percentComplete,
        String message,
        Object data
    ) {
        public static WorkflowStatusResponse fromProgress(Progress progress) {
            WorkflowStatus status = switch (progress.status()) {
                case PENDING -> WorkflowStatus.PENDING;
                case IN_PROGRESS -> WorkflowStatus.RUNNING;
                case COMPLETED -> WorkflowStatus.COMPLETED;
                case FAILED -> WorkflowStatus.FAILED;
                case CANCELLED -> WorkflowStatus.CANCELLED;
            };
            
            return new WorkflowStatusResponse(
                status,
                progress.percentComplete(),
                progress.message(),
                null
            );
        }
        
        public static WorkflowStatusResponse fromState(WorkflowInstance state) {
            WorkflowStatus status = switch (state.getStatus()) {
                case RUNNING -> WorkflowStatus.RUNNING;
                case SUSPENDED -> WorkflowStatus.SUSPENDED;
                case COMPLETED -> WorkflowStatus.COMPLETED;
                case FAILED -> WorkflowStatus.FAILED;
                case CANCELLED -> WorkflowStatus.CANCELLED;
            };
            
            return new WorkflowStatusResponse(
                status,
                state.getStatus() == WorkflowInstance.WorkflowStatus.COMPLETED ? 100 : 0,
                "Workflow " + state.getStatus().toString().toLowerCase(),
                state.getContext() != null ? state.getContext().getStepOutputs().get(Keys.FINAL_RESULT) : null
            );
        }
    }
    
    record WorkflowCurrentResultResponse(
        String runId,
        WorkflowStatus status,
        int percentComplete,
        String message,
        Map<String, Object> data,
        boolean isAsync
    ) {}
    
    record WorkflowCancelResponse(
        String runId,
        boolean cancelled,
        String message
    ) {}
    
}