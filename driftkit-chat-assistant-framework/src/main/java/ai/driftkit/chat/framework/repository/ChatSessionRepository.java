package ai.driftkit.chat.framework.repository;

import ai.driftkit.chat.framework.model.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing chat sessions in MongoDB.
 * This repository is only active when MongoDB is enabled.
 */
@Repository
public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {
    
    /**
     * Find a chat session by chat ID
     * @param chatId The chat ID
     * @return Optional containing the chat session if found
     */
    Optional<ChatSession> findByChatId(String chatId);
    
    /**
     * Find all chats for a user, excluding archived ones, ordered by last message time
     * @param userId The user ID
     * @param pageable Pagination information
     * @return Page of chat sessions
     */
    Page<ChatSession> findByUserIdAndArchivedFalseOrderByLastMessageTimeDesc(String userId, Pageable pageable);
    
    /**
     * Find all non-archived chats, ordered by last message time
     * @param pageable Pagination information
     * @return Page of chat sessions
     */
    Page<ChatSession> findByArchivedFalseOrderByLastMessageTimeDesc(Pageable pageable);
    
    /**
     * Count chats for a user
     * @param userId The user ID
     * @return Number of chats
     */
    long countByUserId(String userId);
    
    /**
     * Count non-archived chats for a user
     * @param userId The user ID
     * @return Number of active chats
     */
    long countByUserIdAndArchivedFalse(String userId);
}