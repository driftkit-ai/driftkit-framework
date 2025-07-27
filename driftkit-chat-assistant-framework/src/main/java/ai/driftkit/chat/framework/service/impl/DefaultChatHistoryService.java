package ai.driftkit.chat.framework.service.impl;

import ai.driftkit.chat.framework.model.ChatDomain.ChatMessage;
import ai.driftkit.chat.framework.model.ChatDomain.ChatRequest;
import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;
import ai.driftkit.chat.framework.repository.ChatMessageRepository;
import ai.driftkit.chat.framework.service.ChatHistoryService;
import ai.driftkit.chat.framework.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of ChatHistoryService.
 * Service for managing chat history.
 * Uses database for persistence with a cache for recently accessed items.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(ChatHistoryService.class)
@RequiredArgsConstructor
public class DefaultChatHistoryService implements ChatHistoryService {
    // Cache for all messages (requests, responses) - cleared periodically, DB is source of truth
    private final Map<String, ChatMessage> messageCache = new ConcurrentHashMap<>();
    
    // Maximum age for cached items in milliseconds (10 minutes)
    private static final long MAX_CACHE_AGE_MS = TimeUnit.MINUTES.toMillis(10);
    
    private final ChatMessageService messageService;
    
    @Autowired(required = false)
    private ChatMessageRepository messageRepository;
    
    /**
     * Add a request to the history
     */
    @Override
    public void addRequest(ChatRequest request) {
        if (request == null || request.getId() == null) {
            log.warn("Cannot add null request or request without ID");
            return;
        }
        
        try {
            // Persist to database
            messageService.addRequest(request.getChatId(), request);
            
            // Update cache after successful DB save
            messageCache.put(request.getId(), request);
            
            log.debug("Added request {} to chat {}", request.getId(), request.getChatId());
        } catch (Exception e) {
            log.error("Error adding request to history", e);
        }
    }
    
    /**
     * Add a response to the history
     */
    @Override
    public void addResponse(ChatResponse response) {
        if (response == null || response.getId() == null) {
            log.warn("Cannot add null response or response without ID");
            return;
        }
        
        try {
            // Persist to database
            messageService.addResponse(response.getChatId(), response);
            
            // Update cache after successful DB save
            messageCache.put(response.getId(), response);
            
            log.debug("Added response {} to chat {}", response.getId(), response.getChatId());
        } catch (Exception e) {
            log.error("Error adding response to history", e);
        }
    }
    
    /**
     * Update an existing response
     */
    @Override
    public void updateResponse(ChatResponse response) {
        if (response == null || response.getId() == null) {
            log.warn("Cannot update null response or response without ID");
            return;
        }
        
        try {
            // Update in database
            messageService.updateResponse(response);
            
            // Update cache after successful DB update
            messageCache.put(response.getId(), response);
            
            log.debug("Updated response {} in chat {}", response.getId(), response.getChatId());
        } catch (Exception e) {
            log.error("Error updating response in history", e);
        }
    }
    
    /**
     * Get a recent request by ID
     */
    @Override
    public ChatRequest getRequest(String requestId) {
        if (requestId == null) {
            return null;
        }
        
        // Check cache first
        ChatMessage message = messageCache.get(requestId);
        if (message instanceof ChatRequest) {
            return (ChatRequest) message;
        }
        
        // If not in cache, query from database
        try {
            message = messageRepository.findById(requestId).orElse(null);
            if (message instanceof ChatRequest) {
                // Update cache
                messageCache.put(requestId, message);
                return (ChatRequest) message;
            }
        } catch (Exception e) {
            log.error("Error getting request from database", e);
        }
        
        return null;
    }
    
    /**
     * Get a recent response by ID
     */
    @Override
    public ChatResponse getResponse(String responseId) {
        if (responseId == null) {
            return null;
        }
        
        // Check cache first
        ChatMessage message = messageCache.get(responseId);
        if (message instanceof ChatResponse) {
            return (ChatResponse) message;
        }
        
        // If not in cache, query from database
        try {
            message = messageRepository.findById(responseId).orElse(null);
            if (message instanceof ChatResponse) {
                // Update cache
                messageCache.put(responseId, message);
                return (ChatResponse) message;
            }
        } catch (Exception e) {
            log.error("Error getting response from database", e);
        }
        
        return null;
    }

    /**
     * Get a message by ID
     */
    @Override
    public ChatMessage getMessage(String messageId) {
        if (messageId == null) {
            return null;
        }
        
        // Check cache first
        ChatMessage message = messageCache.get(messageId);
        if (message != null) {
            return message;
        }
        
        // If not in cache, query from database
        try {
            message = messageRepository.findById(messageId).orElse(null);
            if (message != null) {
                // Update cache
                messageCache.put(messageId, message);
            }
            return message;
        } catch (Exception e) {
            log.error("Error getting message from database", e);
            return null;
        }
    }
    
    /**
     * Get all messages for a chat, ordered by timestamp (newest first)
     */
    @Override
    public Page<ChatMessage> getMessages(String chatId, Pageable pageable) {
        if (chatId == null) {
            return Page.empty();
        }
        
        try {
            // Query from database
            return messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable);
        } catch (Exception e) {
            log.error("Error getting messages for chat {}: {}", chatId, e.getMessage(), e);
            return Page.empty();
        }
    }
    
    /**
     * Clean up old entries from cache every hour to prevent memory leaks
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // Run every hour
    public void cleanup() {
        try {
            long now = System.currentTimeMillis();
            long cutoffTime = now - MAX_CACHE_AGE_MS;
            
            // Clean up message cache
            int messagesBefore = messageCache.size();
            messageCache.entrySet().removeIf(entry -> {
                ChatMessage message = entry.getValue();
                return message.getTimestamp() < cutoffTime;
            });
            
            log.info("Cache cleanup complete. Messages: {} -> {}", 
                    messagesBefore, messageCache.size());
        } catch (Exception e) {
            log.error("Error during cache cleanup", e);
        }
    }
    
}