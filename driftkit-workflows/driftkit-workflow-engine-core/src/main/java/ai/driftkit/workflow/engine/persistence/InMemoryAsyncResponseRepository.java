package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.chat.ChatDomain.ChatResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of AsyncResponseRepository.
 * Suitable for development and testing, not for production distributed systems.
 */
@Slf4j
public class InMemoryAsyncResponseRepository implements AsyncResponseRepository {
    
    private final Map<String, ChatResponse> responses = new ConcurrentHashMap<>();
    
    @Override
    public ChatResponse save(ChatResponse response) {
        responses.put(response.getId(), response);
        log.debug("Saved async response: {}", response.getId());
        return response;
    }
    
    @Override
    public Optional<ChatResponse> findById(String responseId) {
        return Optional.ofNullable(responses.get(responseId));
    }
    
    @Override
    public void deleteById(String responseId) {
        responses.remove(responseId);
        log.debug("Deleted async response: {}", responseId);
    }
    
    @Override
    public boolean existsById(String responseId) {
        return responses.containsKey(responseId);
    }
    
    @Override
    public int deleteOlderThan(long timestampMillis) {
        int deletedCount = 0;
        
        // Note: ChatResponse doesn't have a timestamp field by default
        // In a real implementation, you'd need to track creation time
        // For now, this is a no-op in the in-memory implementation
        log.warn("deleteOlderThan not fully implemented in InMemoryAsyncResponseRepository");
        
        return deletedCount;
    }
}