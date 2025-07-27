package ai.driftkit.chat.framework.service;

import ai.driftkit.chat.framework.model.ChatDomain.ChatMessage;
import ai.driftkit.chat.framework.model.ChatSession;
import ai.driftkit.chat.framework.repository.ChatMessageRepository;
import ai.driftkit.chat.framework.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing chat sessions.
 * Provides functionality for creating, retrieving, updating, and archiving chat sessions.
 * Uses database for persistence with a cache for frequently accessed sessions.
 */
@Slf4j
@Service
public class ChatSessionService {
    
    @Autowired
    private ChatSessionRepository chatRepository;
    
    @Autowired
    private ChatMessageRepository messageRepository;
    
    // Cache for frequently accessed sessions
    private final Map<String, ChatSession> sessionCache = new ConcurrentHashMap<>();
    
    /**
     * Create a new chat session
     */
    public ChatSession createChat(String userId, String name) {
        String chatId = UUID.randomUUID().toString();
        return createChatWithId(chatId, userId, name);
    }
    
    /**
     * Create a new chat session with specific ID
     */
    public ChatSession createChatWithId(String chatId, String userId, String name) {
        long now = System.currentTimeMillis();
        name = StringUtils.isNotBlank(name) ? getDescription(name) : "New Chat - " + new Date(now);

        ChatSession chat = ChatSession.builder()
                .chatId(chatId)
                .userId(userId)
                .name(name)
                .lastMessageTime(now)
                .createdTime(now)
                .updatedTime(now)
                .build();
        
        try {
            ChatSession savedChat = chatRepository.save(chat);
            // Update cache
            sessionCache.put(chatId, savedChat);
            return savedChat;
        } catch (Exception e) {
            log.error("Error creating chat: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create chat", e);
        }
    }
    
    /**
     * Get a chat by ID, creating it if it doesn't exist
     */
    public ChatSession getOrCreateChat(String chatId, String userId, String name) {
        Optional<ChatSession> existing = getChat(chatId);
        return existing.orElseGet(() -> createChatWithId(chatId, userId, name));
    }
    
    /**
     * Get a chat by ID
     */
    public Optional<ChatSession> getChat(String chatId) {
        if (chatId == null) {
            return Optional.empty();
        }
        
        try {
            // Check cache first
            ChatSession cached = sessionCache.get(chatId);
            if (cached != null) {
                return Optional.of(cached);
            }
            
            // If not in cache, query database
            Optional<ChatSession> session = chatRepository.findByChatId(chatId);
            session.ifPresent(s -> sessionCache.put(chatId, s));
            return session;
        } catch (Exception e) {
            log.error("Error getting chat: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * List chats for a user
     */
    public Page<ChatSession> listChatsForUser(String userId, Pageable pageable) {
        if (userId == null) {
            return Page.empty();
        }
        
        try {
            return chatRepository.findByUserIdAndArchivedFalseOrderByLastMessageTimeDesc(userId, pageable);
        } catch (Exception e) {
            log.error("Error listing chats for user {}: {}", userId, e.getMessage(), e);
            return Page.empty();
        }
    }
    
    /**
     * List all chats
     */
    public Page<ChatSession> listAllChats(Pageable pageable) {
        try {
            return chatRepository.findByArchivedFalseOrderByLastMessageTimeDesc(pageable);
        } catch (Exception e) {
            log.error("Error listing all chats: {}", e.getMessage(), e);
            return Page.empty();
        }
    }
    
    /**
     * Update the last message in a chat
     */
    public void updateLastMessage(String chatId, ChatMessage message) {
        if (chatId == null || message == null) {
            return;
        }
        
        try {
            Optional<ChatSession> chatOpt = getChat(chatId);
            
            if (chatOpt.isPresent()) {
                ChatSession chat = chatOpt.get();
                
                String content = message.getPropertiesMap().getOrDefault(
                        "message",
                        message.getPropertiesMap().getOrDefault("messageId", "")
                );

                String description = getDescription(content);

                chat.setDescription(description);
                chat.setLastMessageTime(message.getTimestamp());
                chat.setUpdatedTime(System.currentTimeMillis());
                
                chatRepository.save(chat);
                // Update cache
                sessionCache.put(chatId, chat);
                
                log.debug("Updated last message for chat: {}", chatId);
            } else {
                log.warn("Cannot update last message - chat not found: {}", chatId);
            }
        } catch (Exception e) {
            log.error("Error updating last message for chat: {}", chatId, e);
        }
    }

    /**
     * Archive a chat
     */
    public void archiveChat(String chatId) {
        if (chatId == null) {
            return;
        }
        
        try {
            Optional<ChatSession> chatOpt = getChat(chatId);
            
            if (chatOpt.isPresent()) {
                ChatSession chat = chatOpt.get();
                chat.setArchived(true);
                chat.setUpdatedTime(System.currentTimeMillis());
                
                chatRepository.save(chat);
                // Update cache
                sessionCache.put(chatId, chat);
                
                log.info("Archived chat: {}", chatId);
            } else {
                log.warn("Cannot archive chat - not found: {}", chatId);
            }
        } catch (Exception e) {
            log.error("Error archiving chat: {}", chatId, e);
        }
    }
    
    /**
     * Delete a chat and all its messages
     */
    public void deleteChat(String chatId) {
        if (chatId == null) {
            return;
        }
        
        try {
            // Delete the chat
            chatRepository.deleteById(chatId);
            
            // Delete all messages for this chat
            Page<ChatMessage> messages = messageRepository.findByChatIdOrderByTimestampDesc(chatId, Pageable.unpaged());
            messageRepository.deleteAll(messages);
            log.info("Deleted chat: {} with {} messages", chatId, messages.getContent().size());
            
            // Remove from cache
            sessionCache.remove(chatId);
        } catch (Exception e) {
            log.error("Error deleting chat: {}", chatId, e);
        }
    }
    
    /**
     * Update chat properties
     */
    public void updateChatProperties(String chatId, Map<String, String> properties) {
        if (chatId == null || properties == null || properties.isEmpty()) {
            return;
        }
        
        try {
            Optional<ChatSession> chatOpt = getChat(chatId);
            
            if (chatOpt.isPresent()) {
                ChatSession chat = chatOpt.get();
                chat.getProperties().putAll(properties);
                chat.setUpdatedTime(System.currentTimeMillis());
                
                chatRepository.save(chat);
                // Update cache
                sessionCache.put(chatId, chat);
                
                log.debug("Updated properties for chat: {}", chatId);
            } else {
                log.warn("Cannot update properties - chat not found: {}", chatId);
            }
        } catch (Exception e) {
            log.error("Error updating chat properties: {}", chatId, e);
        }
    }
    
    /**
     * Count chats for a user
     */
    public long countUserChats(String userId, boolean includeArchived) {
        if (userId == null) {
            return 0;
        }
        
        try {
            return includeArchived ? 
                chatRepository.countByUserId(userId) : 
                chatRepository.countByUserIdAndArchivedFalse(userId);
        } catch (Exception e) {
            log.error("Error counting chats for user {}: {}", userId, e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Helper method to get description from content
     */
    @NotNull
    private static String getDescription(String content) {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        
        String description;
        if (content.length() > 100) {
            description = content.substring(0, 97) + "...";
        } else {
            description = content;
        }
        return description;
    }
    
    /**
     * Clean up cache periodically
     * Run every 30 minutes
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void cleanupCache() {
        try {
            int initialSize = sessionCache.size();
            // Clear the entire cache - it will be repopulated on demand
            sessionCache.clear();
            log.info("Session cache cleanup: cleared {} entries", initialSize);
        } catch (Exception e) {
            log.error("Error during session cache cleanup", e);
        }
    }
}