package ai.driftkit.chat.framework.controller;

import ai.driftkit.chat.framework.ai.domain.AIFunctionSchema;
import ai.driftkit.chat.framework.dto.PageableResponseWithChat;
import ai.driftkit.chat.framework.dto.PageableResponseWithChatMessage;
import ai.driftkit.chat.framework.model.ChatDomain.ChatMessage;
import ai.driftkit.chat.framework.model.ChatDomain.ChatRequest;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;
import ai.driftkit.chat.framework.model.ChatDomain.MessageType;
import ai.driftkit.chat.framework.model.ChatMessageTask;
import ai.driftkit.chat.framework.model.ChatMessageTaskConverter;
import ai.driftkit.chat.framework.model.ChatSession;
import ai.driftkit.chat.framework.model.StepDefinition;
import ai.driftkit.chat.framework.repository.ChatMessageRepository;
import ai.driftkit.chat.framework.service.AsyncResponseTracker;
import ai.driftkit.chat.framework.service.ChatSessionService;
import ai.driftkit.chat.framework.service.ChatWorkflowService;
import ai.driftkit.chat.framework.workflow.AnnotatedWorkflow;
import ai.driftkit.chat.framework.workflow.WorkflowRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/public/api1.0/ai/assistant/v2/")
@Validated
public class AssistantController {
    private final ChatWorkflowService chatWorkflowService;
    private final AsyncResponseTracker asyncResponseTracker;
    private final ChatSessionService chatService;
    
    private final ChatMessageRepository messageRepository;

    @PostMapping("/chat")
    public ChatResponseWithTasks chat(@Valid @RequestBody ChatRequest request,
                            @RequestParam(required = false) String userId) {
        userId = decode(userId);

        log.info("Processing chat request for session: {}, user: {}", request.getChatId(), userId);
        
        try {
            if (StringUtils.isEmpty(request.getUserId())) {
                if (StringUtils.isNotBlank(userId)) {
                    request.setUserId(userId);
                } else {
                    userId = request.getPropertiesMap().getOrDefault("userId", "anonymous");
                    request.setUserId(userId);
                }
            }

            // Ensure chat exists
            chatService.getOrCreateChat(request.getChatId(), userId, request.getMessage());
            
            // Process the request using workflow service (which will handle session creation if needed)
            ChatResponse response = chatWorkflowService.processChat(request);
            
            // Convert the request and response to message tasks
            List<ChatMessageTask> requestTasks = ChatMessageTaskConverter.convert(request);
            List<ChatMessageTask> responseTasks = ChatMessageTaskConverter.convert(response);
            
            // Create a structured response with both the original response and the message tasks
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

    private static String decode(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        return URLDecoder.decode(userId, Charset.defaultCharset());
    }

    // Poll for response updates
    @GetMapping("/chat/response/{responseId}")
    public ChatResponseWithTasks getChatResponse(
            @PathVariable String responseId,
            @RequestParam(required = false) String userId
    ) {
        userId = decode(userId);
        log.info("Getting chat response for ID: {}, user: {}", responseId, userId);
        
        try {
            Optional<ChatResponse> response = asyncResponseTracker.getResponse(responseId);
            
            // If userId is provided, verify ownership
            if (response.isPresent() && StringUtils.isNotBlank(userId)) {
                ChatResponse chatResponse = response.get();
                String responseUserId = chatResponse.getUserId();
                
                // If the response has a userId that doesn't match, return forbidden
                if (StringUtils.isNotBlank(responseUserId) && !responseUserId.equals(userId)) {
                    log.warn("User {} attempted to access response {} owned by {}", 
                        userId, responseId, responseUserId);
                    throw new RuntimeException("Forbidden for [%s] [%s]".formatted(userId, responseId));
                }
            }

            if (response.isEmpty()) {
                return new ChatResponseWithTasks();
            }

            List<ChatMessageTask> responseTasks = ChatMessageTaskConverter.convert(response.get());

            return new ChatResponseWithTasks(response.get(), null, responseTasks);
        } catch (Exception e) {
            log.error("Error retrieving chat response", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error retrieving chat response: " + e.getMessage(),
                e
            );
        }
    }
    
    // Chat history with pagination
    @GetMapping("/chat/history")
    public List<ChatMessageTask> history(
            final @NotNull HttpServletRequest request,
            @RequestParam String chatId,
            @RequestParam(required = false) String userId, 
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "1000") int limit,
            @RequestParam(required = false, defaultValue = "asc") String sort,
            @RequestParam(required = false, defaultValue = "true") boolean showSchema,
            @RequestParam(required = false, defaultValue = "false") Boolean context) {
        userId = decode(userId);
        log.info("Retrieving chat history for session: {}, user: {}, page: {}, limit: {}, sort: {}",
                chatId, userId, page, limit, sort);
        
        try {
            // Create pageable object with sort by timestamp
            Pageable pageable = createPageable(page, limit, sort, "timestamp");
            
            // Verify user has access to the chat if userId provided
            verifyUserChatAccess(chatId, userId);
            
            // Get chat history from repository with pagination
            Page<ChatMessage> historyPage = messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable);
            
            // If no history found, return empty page (don't try to get history from other chats)
            if (historyPage.isEmpty()) {
                log.info("No history found for chat: {}", chatId);
                return new ArrayList<>();
            }

            List<ChatMessage> content = historyPage.getContent().stream()
                    .filter(e -> BooleanUtils.isTrue(context) || e.getType() != MessageType.CONTEXT)
                    .toList();

            if (BooleanUtils.isNotTrue(showSchema)) {
                content = content.stream()
                        .map(SerializationUtils::clone)
                        .peek(e -> {
                            if (e instanceof ChatResponse response) {
                                // Clear schemas to reduce payload size
                                response.setNextSchemaAsSchema(null);
                            }
                        })
                        .toList();
            }
            
            // Convert messages to MessageTask format and return only the tasks
            return ChatMessageTaskConverter.convertAll(content);
        } catch (ResponseStatusException e) {
            // Re-throw existing status exceptions
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

    // Chat history with pagination
    @GetMapping("/chat/history/pageable")
    public PageableResponseWithChatMessage historyPageable(
            final @NotNull HttpServletRequest request,
            @RequestParam String chatId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false, defaultValue = "asc") String sort) {
        userId = decode(userId);
        log.info("Retrieving chat history for session: {}, user: {}, page: {}, limit: {}, sort: {}",
                chatId, userId, page, limit, sort);

        try {
            // Create pageable object with sort by timestamp
            Pageable pageable = createPageable(page, limit, sort, "timestamp");

            // Verify user has access to the chat if userId provided
            verifyUserChatAccess(chatId, userId);

            // Get chat history from repository with pagination
            Page<ChatMessage> historyPage = messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable);

            // If no history found, return empty page (don't try to get history from other chats)
            if (historyPage.isEmpty()) {
                log.info("No history found for chat: {}", chatId);
            }

            return new PageableResponseWithChatMessage(request, historyPage);
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

    // List available chats with pagination
    @GetMapping("/chat/list")
    public PageableResponseWithChat getChats(
            final @NotNull HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int limit,
            @RequestParam(required = false, defaultValue = "desc") String sort) {
        userId = decode(userId);
        log.info("Listing chats for user: {}, page: {}, limit: {}, sort: {}", userId, page, limit, sort);
        
        try {
            // Create pageable object with sort by lastMessageTime
            Pageable pageable = createPageable(page, limit, sort, "lastMessageTime");
            
            // Get paginated chats only for the specified user
            // If userId is not provided, return an empty page
            Page<ChatSession> chatsPage;
            if (StringUtils.isNotBlank(userId)) {
                chatsPage = chatService.listChatsForUser(userId, pageable);
            } else {
                // Return empty page if no userId provided
                chatsPage = Page.empty(pageable);
            }

            return new PageableResponseWithChat(request, chatsPage);
        } catch (Exception e) {
            log.error("Error listing chats", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error listing chats: " + e.getMessage(),
                e
            );
        }
    }
    
    // Create a new chat
    @PostMapping("/chat/create")
    public ChatInfo createChat(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String name
    ) {
        userId = decode(userId);
        userId = StringUtils.isNotBlank(userId) ? userId : "anonymous";
        log.info("Creating new chat for user: {}", userId);
        
        try {
            ChatSession chat = chatService.createChat(userId, name);
            
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
    
    // Archive a chat
    @PostMapping("/chat/{chatId}/archive")
    public ResponseEntity<Void> archiveChat(
            @PathVariable String chatId,
            @RequestParam(required = false) String userId) {
        userId = decode(userId);
        log.info("Archiving chat: {}, user: {}", chatId, userId);
        
        try {
            // Verify user has access to the chat if userId provided
            verifyUserChatAccess(chatId, userId);
            
            chatService.archiveChat(chatId);
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            // Return as ResponseEntity for consistency
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
    
    // Schemas endpoint - list all available schemas
    @GetMapping("/schemas")
    public SchemaResponse schemas() {
        log.info("Retrieving schemas for all workflows");
        
        try {
            // Get all unique schemas from registered workflows
            Set<AIFunctionSchema> schemas = new HashSet<>();
            Map<String, String> messageIds = new HashMap<>();
            
            // Collect schemas from registered AnnotatedWorkflows in WorkflowRegistry
            for (AnnotatedWorkflow workflow : WorkflowRegistry.getAllWorkflows()) {
                for (StepDefinition step : workflow.getStepDefinitions()) {
                    if (step.getInputSchemas() != null) {
                        schemas.addAll(step.getInputSchemas());
                    }
                    if (step.getOutputSchemas() != null) {
                        schemas.addAll(step.getOutputSchemas());
                    }
                }
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
     * Chat information for the chat list
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
    
    /**
     * Schema response for the schemas endpoint
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaResponse {
        private List<AIFunctionSchema> schemas;
        private Map<String, String> messageIds;
    }
    
    /**
     * Helper methods for common operations
     */
    
    /**
     * Creates a PageRequest object with the provided pagination parameters
     * @param page Page number (zero-based)
     * @param limit Number of items per page
     * @param sort Sort direction ("asc" or "desc")
     * @param sortBy Field to sort by
     * @return Configured Pageable object
     */
    private Pageable createPageable(int page, int limit, String sort, String sortBy) {
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(sort) 
            ? Sort.Direction.ASC 
            : Sort.Direction.DESC;
        
        return PageRequest.of(page, limit, Sort.by(sortDirection, sortBy));
    }
    
    /**
     * Verifies that the user has access to the chat
     * @param chatId Chat ID to verify access to
     * @param userId User ID to check
     * @throws ResponseStatusException if user is not authorized to access the chat
     */
    private void verifyUserChatAccess(String chatId, String userId) {
        if (StringUtils.isNotBlank(userId)) {
            Optional<ChatSession> chatOpt = chatService.getChat(chatId);
            if (chatOpt.isPresent() && !userId.equals(chatOpt.get().getUserId())) {
                log.warn("User {} attempted to access chat {} owned by {}", 
                    userId, chatId, chatOpt.get().getUserId());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized to access this chat");
            }
        }
    }
    
    /**
     * Get the first step schema for a workflow by ID
     * This endpoint allows the frontend to understand how to initialize the first step for each workflow
     * 
     * @param workflowId The ID of the workflow
     * @return First step schema response containing the schema(s) for the first step
     */
    @GetMapping("/workflow/first-schema/{workflowId}")
    public FirstStepSchemaResponse getFirstStepSchema(@PathVariable String workflowId) {
        log.info("Getting first step schema for workflow: {}", workflowId);
        
        try {
            // Get the workflow by ID
            Optional<AnnotatedWorkflow> workflowOpt = WorkflowRegistry.getWorkflow(workflowId);
            
            if (workflowOpt.isEmpty()) {
                log.warn("Workflow not found with ID: {}", workflowId);
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, 
                    "Workflow not found with ID: " + workflowId
                );
            }
            
            AnnotatedWorkflow workflow = workflowOpt.get();
            
            // Get all steps in the workflow
            List<StepDefinition> steps = workflow.getStepDefinitions();
            
            if (steps.isEmpty()) {
                log.warn("No steps found for workflow: {}", workflowId);
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, 
                    "No steps found for workflow: " + workflowId
                );
            }
            
            // The first step is the first in the sorted list (sorted by index)
            StepDefinition firstStep = steps.get(0);
            
            // Get the input schemas for the first step
            List<AIFunctionSchema> inputSchemas = firstStep.getInputSchemas();
            
            if (inputSchemas == null || inputSchemas.isEmpty()) {
                log.warn("No input schemas found for the first step of workflow: {}", workflowId);
                return new FirstStepSchemaResponse(workflowId, firstStep.getId(), Collections.emptyList());
            }
            
            return new FirstStepSchemaResponse(workflowId, firstStep.getId(), inputSchemas);
            
        } catch (ResponseStatusException e) {
            // Re-throw existing status exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving first step schema", e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "Error retrieving first step schema: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Response for the first step schema endpoint
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FirstStepSchemaResponse {
        private String workflowId;
        private String stepId;
        private List<AIFunctionSchema> schemas;
    }
    
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
}