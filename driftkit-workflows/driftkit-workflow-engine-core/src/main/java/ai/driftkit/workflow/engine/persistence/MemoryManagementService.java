package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.common.service.ChatStore;
import ai.driftkit.workflow.engine.domain.ChatSession;
import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import ai.driftkit.workflow.engine.domain.AsyncStepState;
import ai.driftkit.workflow.engine.domain.SuspensionData;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryAsyncStepStateRepository;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemoryChatSessionRepository;
import ai.driftkit.workflow.engine.persistence.inmemory.InMemorySuspensionDataRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Service for managing chat sessions and suspension data.
 * Chat history is now managed by ChatStore directly.
 */
@Slf4j
public class MemoryManagementService {
    
    private final ChatSessionRepository sessionRepository;
    private final AsyncStepStateRepository asyncStepStateRepository;
    private final SuspensionDataRepository suspensionDataRepository;
    
    // Constructor
    public MemoryManagementService(
            ChatSessionRepository sessionRepository) {
        this(sessionRepository,
             new InMemoryAsyncStepStateRepository(),
             new InMemorySuspensionDataRepository());
    }
    
    // Constructor with all repositories
    public MemoryManagementService(
            ChatSessionRepository sessionRepository,
            AsyncStepStateRepository asyncStepStateRepository,
            SuspensionDataRepository suspensionDataRepository) {
        this.sessionRepository = sessionRepository;
        this.asyncStepStateRepository = asyncStepStateRepository;
        this.suspensionDataRepository = suspensionDataRepository;
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
     * Update session last message time.
     */
    public void updateSessionLastMessageTime(String chatId, long timestamp) {
        sessionRepository.findById(chatId).ifPresent(session -> {
            ChatSession updated = session.withLastMessageTime(timestamp);
            sessionRepository.save(updated);
        });
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
     * Delete chat session.
     */
    public void deleteChat(String chatId) {
        sessionRepository.deleteById(chatId);
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
     * Create default in-memory service.
     */
    public static MemoryManagementService createDefault() {
        return new MemoryManagementService(
            new InMemoryChatSessionRepository()
        );
    }
    
    
    private String abbreviate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
    
    // ========== Suspension Data Methods ==========
    
    /**
     * Save suspension data for a workflow instance.
     */
    public void saveSuspensionData(String instanceId, SuspensionData suspensionData) {
        suspensionDataRepository.save(instanceId, suspensionData);
    }
    
    /**
     * Get suspension data by instance ID.
     */
    public Optional<SuspensionData> getSuspensionData(String instanceId) {
        return suspensionDataRepository.findByInstanceId(instanceId);
    }
    
    /**
     * Get suspension data by message ID.
     */
    public Optional<SuspensionData> getSuspensionDataByMessageId(String messageId) {
        return suspensionDataRepository.findByMessageId(messageId);
    }
    
    /**
     * Delete suspension data for a workflow instance.
     */
    public void deleteSuspensionData(String instanceId) {
        suspensionDataRepository.deleteByInstanceId(instanceId);
    }
}