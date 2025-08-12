package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.common.domain.Message;
import ai.driftkit.common.domain.ChatMessageType;
import ai.driftkit.common.domain.MessageType;
import ai.driftkit.common.service.ChatMemory;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.workflow.engine.chat.ChatDomain;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatMessage;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatRequest;
import ai.driftkit.workflow.engine.chat.ChatDomain.ChatResponse;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import ai.driftkit.workflow.engine.domain.AsyncStepState;
import ai.driftkit.workflow.engine.memory.WorkflowMemoryConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;

/**
 * Service for managing chat sessions and history memory.
 * Provides a unified interface for memory management without Spring dependencies.
 * Supports distributed systems by using repositories instead of local caches.
 * Integrates with driftkit-common ChatMemory for token-based memory management.
 */
@Slf4j
public class MemoryManagementService {
    
    private final ChatSessionRepository sessionRepository;
    private final ChatHistoryRepository historyRepository;
    private final AsyncResponseRepository asyncResponseRepository;
    private final AsyncStepStateRepository asyncStepStateRepository;
    private final WorkflowMemoryConfiguration memoryConfiguration;
    
    // Constructor for backward compatibility
    public MemoryManagementService(
            ChatSessionRepository sessionRepository,
            ChatHistoryRepository historyRepository,
            AsyncResponseRepository asyncResponseRepository,
            WorkflowMemoryConfiguration memoryConfiguration) {
        this(sessionRepository, historyRepository, asyncResponseRepository, 
             new InMemoryAsyncStepStateRepository(), memoryConfiguration);
    }
    
    // Full constructor
    public MemoryManagementService(
            ChatSessionRepository sessionRepository,
            ChatHistoryRepository historyRepository,
            AsyncResponseRepository asyncResponseRepository,
            AsyncStepStateRepository asyncStepStateRepository,
            WorkflowMemoryConfiguration memoryConfiguration) {
        this.sessionRepository = sessionRepository;
        this.historyRepository = historyRepository;
        this.asyncResponseRepository = asyncResponseRepository;
        this.asyncStepStateRepository = asyncStepStateRepository;
        this.memoryConfiguration = memoryConfiguration;
    }
    
    /**
     * Get or create a chat session.
     */
    public ChatSession getOrCreateChatSession(String chatId, String userId, String initialMessage) {
        Optional<ChatSession> existing = sessionRepository.findById(chatId);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        String name = StringUtils.isEmpty(initialMessage)
                ? "New Chat"
                : abbreviate(initialMessage, 50);
        
        ChatSession session = ChatSession.create(chatId, userId, name);
        return sessionRepository.save(session);
    }
    
    /**
     * Get a chat session by ID.
     */
    public Optional<ChatSession> getChatSession(String chatId) {
        return sessionRepository.findById(chatId);
    }
    
    /**
     * Create a new chat session.
     */
    public ChatSession createChatSession(String userId, String name) {
        String chatId = UUID.randomUUID().toString();
        ChatSession session = ChatSession.create(chatId, userId, name);
        return sessionRepository.save(session);
    }
    
    /**
     * Archive a chat session.
     */
    public void archiveChatSession(String chatId) {
        sessionRepository.findById(chatId).ifPresent(session -> {
            ChatSession archived = session.archive();
            sessionRepository.save(archived);
        });
    }
    
    /**
     * List active chats for a user.
     */
    public PageResult<ChatSession> listActiveChatsForUser(String userId, PageRequest pageRequest) {
        if (StringUtils.isEmpty(userId)) {
            return PageResult.empty(pageRequest.getPageNumber(), pageRequest.getPageSize());
        }
        return sessionRepository.findActiveByUserId(userId, pageRequest);
    }
    
    /**
     * Add a message to chat history and update session.
     */
    public void addToChatHistory(String chatId, ChatMessage message) {
        historyRepository.addMessage(chatId, message);
        
        // Update session last message time
        sessionRepository.findById(chatId).ifPresent(session -> {
            ChatSession updated = session.withLastMessageTime(message.getTimestamp());
            sessionRepository.save(updated);
        });
    }
    
    /**
     * Store request in chat history.
     */
    public void storeChatRequest(ChatRequest request) {
        addToChatHistory(request.getChatId(), request);
    }
    
    /**
     * Store response in chat history.
     */
    public void storeChatResponse(ChatResponse response) {
        // Store in history
        addToChatHistory(response.getChatId(), response);
        
        // If async, store in async repository for distributed access
        if (!response.isCompleted()) {
            asyncResponseRepository.save(response);
        }
    }
    
    /**
     * Get async response from repository (works across nodes).
     */
    public Optional<ChatResponse> getAsyncResponse(String responseId) {
        return asyncResponseRepository.findById(responseId);
    }
    
    /**
     * Update async response.
     */
    public void updateAsyncResponse(String responseId, ChatResponse updatedResponse) {
        // Save updated response
        asyncResponseRepository.save(updatedResponse);
        
        // If completed, remove from async repository and ensure it's in history
        if (updatedResponse.isCompleted()) {
            asyncResponseRepository.deleteById(responseId);
            addToChatHistory(updatedResponse.getChatId(), updatedResponse);
        }
    }
    
    /**
     * Get async step state by message ID.
     */
    public Optional<AsyncStepState> getAsyncStepState(String messageId) {
        return asyncStepStateRepository.findByMessageId(messageId);
    }
    
    /**
     * Update async step state progress.
     */
    public void updateAsyncStepStateProgress(String messageId, int percentComplete, String statusMessage) {
        asyncStepStateRepository.updateProgress(messageId, percentComplete, statusMessage);
    }
    
    /**
     * Get chat history.
     */
    public PageResult<ChatMessage> getChatHistory(String chatId, PageRequest pageRequest, boolean includeContext) {
        return historyRepository.findByChatId(chatId, pageRequest, includeContext);
    }
    
    /**
     * Get recent chat messages.
     */
    public List<ChatMessage> getRecentMessages(String chatId, int limit) {
        return historyRepository.findRecentByChatId(chatId, limit);
    }
    
    /**
     * Delete chat session and history.
     */
    public void deleteChat(String chatId) {
        sessionRepository.deleteById(chatId);
        historyRepository.deleteByChatId(chatId);
    }
    
    /**
     * Verify user has access to chat.
     */
    public boolean verifyUserAccess(String chatId, String userId) {
        if (StringUtils.isEmpty(userId)) {
            return true; // Allow anonymous access if no userId provided
        }
        
        Optional<ChatSession> session = sessionRepository.findById(chatId);
        return session.map(s -> userId.equals(s.getUserId())).orElse(false);
    }
    
    /**
     * Get message count for a chat.
     */
    public long getMessageCount(String chatId) {
        return historyRepository.countByChatId(chatId);
    }
    
    /**
     * Clean up old async responses.
     * Should be called periodically to prevent unbounded growth.
     */
    public int cleanupOldAsyncResponses(long olderThanHours) {
        long cutoffTime = System.currentTimeMillis() - (olderThanHours * 3600 * 1000);
        return asyncResponseRepository.deleteOlderThan(cutoffTime);
    }
    
    /**
     * Create default in-memory service.
     */
    public static MemoryManagementService createDefault() {
        ChatHistoryRepository historyRepo = new InMemoryChatHistoryRepository();
        return new MemoryManagementService(
            new InMemoryChatSessionRepository(),
            historyRepo,
            new InMemoryAsyncResponseRepository(),
            WorkflowMemoryConfiguration.createDefault(historyRepo)
        );
    }
    
    /**
     * Create default in-memory service without memory configuration.
     */
    public static MemoryManagementService createDefaultWithoutMemory() {
        return new MemoryManagementService(
            new InMemoryChatSessionRepository(),
            new InMemoryChatHistoryRepository(),
            new InMemoryAsyncResponseRepository(),
            null
        );
    }
    
    /**
     * Get ChatMemory instance for a chat.
     * Uses token-based memory management from driftkit-common.
     */
    public ChatMemory getChatMemory(String chatId) {
        if (memoryConfiguration == null) {
            throw new IllegalStateException("Memory configuration is not set");
        }
        return memoryConfiguration.createChatMemory(chatId);
    }
    
    /**
     * Add a message to ChatMemory.
     * Converts workflow message types to common Message format.
     */
    public void addToMemory(String chatId, ChatMessage message) {
        if (memoryConfiguration == null) {
            return; // Skip if no memory configuration
        }
        
        ChatMemory memory = getChatMemory(chatId);
        Message commonMessage = convertToCommonMessage(message);
        memory.add(commonMessage);
    }
    
    /**
     * Add a ChatRequest to memory.
     */
    @SneakyThrows
    public void addRequestToMemory(ChatRequest request) {
        if (memoryConfiguration == null) {
            return; // Skip if no memory configuration
        }
        
        ChatMemory memory = getChatMemory(request.getChatId());
        
        // Convert ChatRequest to Message
        String content = request.getMessage();
        if (StringUtils.isEmpty(content) && request.getProperties() != null && !request.getProperties().isEmpty()) {
            content = JsonUtils.toJson(request.getProperties());
        }
        
        Message message = Message.builder()
            .messageId(request.getId())
            .message(StringUtils.defaultIfEmpty(content, ""))
            .type(ChatMessageType.USER)
            .messageType(MessageType.TEXT)
            .createdTime(request.getTimestamp() != null ? request.getTimestamp() : System.currentTimeMillis())
            .requestInitTime(request.getTimestamp() != null ? request.getTimestamp() : System.currentTimeMillis())
            .build();
            
        memory.add(message);
    }
    
    /**
     * Add a ChatResponse to memory.
     */
    @SneakyThrows
    public void addResponseToMemory(ChatResponse response) {
        if (memoryConfiguration == null) {
            return; // Skip if no memory configuration
        }
        
        ChatMemory memory = getChatMemory(response.getChatId());
        
        // Convert ChatResponse to Message - always use properties as JSON
        String content = null;
        if (!response.getProperties().isEmpty()) {
            content = JsonUtils.toJson(response.getPropertiesMap());
        }
        
        Message message = Message.builder()
            .messageId(response.getId())
            .message(StringUtils.defaultIfEmpty(content, ""))
            .type(ChatMessageType.AI)
            .messageType(MessageType.TEXT)
            .createdTime(response.getTimestamp() != null ? response.getTimestamp() : System.currentTimeMillis())
            .requestInitTime(response.getTimestamp() != null ? response.getTimestamp() : System.currentTimeMillis())
            .responseTime(response.getTimestamp())
            .build();
            
        memory.add(message);
    }
    
    /**
     * Convert workflow ChatMessage to common Message.
     */
    @SneakyThrows
    private Message convertToCommonMessage(ChatMessage chatMessage) {
        ChatMessageType type = convertChatMessageTypeToCommonType(chatMessage.getType());
        
        // Convert properties to JSON
        String content = null;
        if (!chatMessage.getProperties().isEmpty()) {
            content = JsonUtils.toJson(chatMessage.getPropertiesMap());
        }
        
        return Message.builder()
            .messageId(chatMessage.getId())
            .message(StringUtils.defaultIfEmpty(content, ""))
            .type(type)
            .messageType(MessageType.TEXT)
            .createdTime(chatMessage.getTimestamp() != null ? chatMessage.getTimestamp() : System.currentTimeMillis())
            .requestInitTime(chatMessage.getTimestamp() != null ? chatMessage.getTimestamp() : System.currentTimeMillis())
            .build();
    }
    
    /**
     * Convert ChatDomain.MessageType to ChatMessageType.
     */
    private ChatMessageType convertChatMessageTypeToCommonType(ChatDomain.MessageType type) {
        if (type == null) {
            return ChatMessageType.USER;
        }
        switch (type) {
            case USER:
                return ChatMessageType.USER;
            case AI:
                return ChatMessageType.AI;
            case SYSTEM:
                return ChatMessageType.SYSTEM;
            default:
                log.warn("Unknown type: {}, defaulting to USER", type);
                return ChatMessageType.USER;
        }
    }
    
    private String abbreviate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}