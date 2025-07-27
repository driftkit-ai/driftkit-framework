package ai.driftkit.chat.framework.repository;

import ai.driftkit.chat.framework.model.ChatDomain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing chat messages in MongoDB.
 * This repository is only active when MongoDB is enabled.
 */
@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    
    /**
     * Find all messages for a chat, ordered by timestamp descending
     * @param chatId The chat ID
     * @param pageable Pagination information
     * @return Page of chat messages
     */
    Page<ChatMessage> findByChatIdOrderByTimestampDesc(String chatId, Pageable pageable);
    
    /**
     * Delete all messages for a chat
     * @param chatId The chat ID
     * @return Number of deleted messages
     */
    long deleteByChatId(String chatId);
    
    /**
     * Count messages for a chat
     * @param chatId The chat ID
     * @return Number of messages
     */
    long countByChatId(String chatId);
}