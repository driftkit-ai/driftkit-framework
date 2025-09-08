package ai.driftkit.workflow.controllers.controller;

import ai.driftkit.common.domain.chat.ChatMessage;
import ai.driftkit.common.domain.chat.ChatRequest;
import ai.driftkit.common.domain.chat.ChatResponse;
import ai.driftkit.workflow.engine.async.ProgressTracker;
import ai.driftkit.workflow.engine.chat.ChatMessageTask;
import ai.driftkit.workflow.engine.chat.converter.ChatMessageTaskConverter;
import ai.driftkit.workflow.engine.core.WorkflowEngine;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.StepMetadata;
import ai.driftkit.workflow.engine.domain.WorkflowDetails;
import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import ai.driftkit.workflow.engine.schema.AIFunctionSchema;
import ai.driftkit.workflow.engine.spring.dto.PageableResponseWithChat;
import ai.driftkit.workflow.engine.spring.dto.PageableResponseWithChatMessage;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.apache.commons.lang3.StringUtils;
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
@RequestMapping("/public/api1.0/ai/assistant/")
@Validated
public class AssistantController {
    
    private final WorkflowEngine engine;
    private final ProgressTracker progressTracker;
    private final WorkflowService workflowService;
    
    @PostMapping("/chat")
    public ChatResponseWithTasks chat(
            @Valid @RequestBody ChatRequest request,
            @RequestParam(required = false) String userId
    ) {
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
            
            workflowService.getOrCreateChatSession(request.getChatId(), userId, request.getMessage());
            
            ChatResponse response = workflowService.processChatRequest(request);
            
            List<ChatMessageTask> requestTasks = ChatMessageTaskConverter.convert(request);
            List<ChatMessageTask> responseTasks = ChatMessageTaskConverter.convert(response);
            
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
    
    @GetMapping("/chat/response/{responseId}")
    public ChatResponseWithTasks getChatResponse(
            @PathVariable String responseId,
            @RequestParam(required = false) String userId
    ) {
        userId = decode(userId);
        log.info("Getting chat response for ID: {}, user: {}", responseId, userId);
        
        try {
            Optional<ChatResponse> response = workflowService.getChatResponse(responseId);
            
            if (response.isPresent() && StringUtils.isNotBlank(userId)) {
                ChatResponse chatResponse = response.get();
                String responseUserId = chatResponse.getUserId();
                
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
    
    @GetMapping("/chat/history")
    public List<ChatMessageTask> history(
            HttpServletRequest request,
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
            
            verifyUserChatAccess(chatId, userId);
            
            Page<ChatMessage> historyPage = workflowService.getChatHistory(chatId, pageable, context, showSchema);
            
            if (historyPage.isEmpty()) {
                log.info("No history found for chat: {}", chatId);
                return new ArrayList<>();
            }
            
            List<ChatMessage> content = historyPage.getContent().stream()
                    .filter(e -> BooleanUtils.isTrue(context) || e.getType() != ChatMessage.MessageType.CONTEXT)
                    .toList();
            
            if (BooleanUtils.isNotTrue(showSchema)) {
                content = content.stream()
                        .map(SerializationUtils::clone)
                        .peek(e -> {
                            if (e instanceof ChatResponse response) {
                                // Clear schemas to reduce payload size
                                response.setNextSchema(null);
                            }
                        })
                        .toList();
            }
            
            return ChatMessageTaskConverter.convertAll(content);
            
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
    
    @GetMapping("/chat/history/pageable")
    public PageableResponseWithChatMessage historyPageable(
            HttpServletRequest request,
            @RequestParam String chatId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @RequestParam(required = false, defaultValue = "asc") String sort
    ) {
        userId = decode(userId);
        log.info("Retrieving chat history for session: {}, user: {}, page: {}, limit: {}, sort: {}",
                chatId, userId, page, limit, sort);
        
        try {
            Pageable pageable = createPageable(page, limit, sort, "timestamp");
            
            verifyUserChatAccess(chatId, userId);
            
            Page<ChatMessage> historyPage = workflowService.getChatHistory(chatId, pageable, false, true);
            
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
    
    @GetMapping("/chat/list")
    public PageableResponseWithChat getChats(
            HttpServletRequest request,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int limit,
            @RequestParam(required = false, defaultValue = "desc") String sort
    ) {
        userId = decode(userId);
        log.info("Listing chats for user: {}, page: {}, limit: {}, sort: {}", userId, page, limit, sort);
        
        try {
            Pageable pageable = createPageable(page, limit, sort, "lastMessageTime");
            
            Page<ChatSession> chatsPage;
            if (StringUtils.isNotBlank(userId)) {
                chatsPage = workflowService.listChatsForUser(userId, pageable);
            } else {
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
    
    @PostMapping("/chat/create")
    public ChatInfo createChat(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String name
    ) {
        userId = decode(userId);
        userId = StringUtils.isNotBlank(userId) ? userId : "anonymous";
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
    
    @GetMapping("/schemas")
    public SchemaResponse schemas() {
        log.info("Retrieving schemas for all workflows");
        
        try {
            Set<AIFunctionSchema> schemas = new HashSet<>();
            Map<String, String> messageIds = new HashMap<>();
            
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
    
    @GetMapping("/workflow/first-schema/{workflowId}")
    public FirstStepSchemaResponse getFirstStepSchema(@PathVariable String workflowId) {
        log.info("Getting first step schema for workflow: {}", workflowId);
        
        try {
            WorkflowDetails details = workflowService.getWorkflowDetails(workflowId);
            
            if (details == null) {
                log.warn("Workflow not found with ID: {}", workflowId);
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Workflow not found with ID: " + workflowId
                );
            }
            
            List<StepMetadata> steps = details.steps();
            
            if (steps.isEmpty()) {
                log.warn("No steps found for workflow: {}", workflowId);
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No steps found for workflow: " + workflowId
                );
            }
            
            StepMetadata firstStep = steps.get(0);
            
            List<AIFunctionSchema> inputSchemas = firstStep.inputSchema() != null
                ? List.of(firstStep.inputSchema())
                : Collections.emptyList();
            
            if (inputSchemas.isEmpty()) {
                log.warn("No input schemas found for the first step of workflow: {}", workflowId);
                return new FirstStepSchemaResponse(workflowId, firstStep.id(), Collections.emptyList());
            }
            
            return new FirstStepSchemaResponse(workflowId, firstStep.id(), inputSchemas);
            
        } catch (ResponseStatusException e) {
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
    
    private static String decode(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        return URLDecoder.decode(userId, Charset.defaultCharset());
    }
    
    private Pageable createPageable(int page, int limit, String sort, String sortBy) {
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(sort)
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
        
        return PageRequest.of(page, limit, Sort.by(sortDirection, sortBy));
    }
    
    private void verifyUserChatAccess(String chatId, String userId) {
        if (StringUtils.isNotBlank(userId)) {
            Optional<ChatSession> chatOpt = workflowService.getChatSession(chatId);
            if (chatOpt.isPresent() && !userId.equals(chatOpt.get().getUserId())) {
                log.warn("User {} attempted to access chat {} owned by {}",
                    userId, chatId, chatOpt.get().getUserId());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not authorized to access this chat");
            }
        }
    }
    
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
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponseWithTasks {
        private ChatResponse originalResponse;
        private List<ChatMessageTask> request;
        private List<ChatMessageTask> response;
    }
}