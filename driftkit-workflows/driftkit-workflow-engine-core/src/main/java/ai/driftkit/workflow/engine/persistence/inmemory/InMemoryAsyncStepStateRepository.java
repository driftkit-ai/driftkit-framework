package ai.driftkit.workflow.engine.persistence.inmemory;

import ai.driftkit.workflow.engine.domain.AsyncStepState;
import ai.driftkit.workflow.engine.persistence.AsyncStepStateRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of AsyncStepStateRepository.
 * Suitable for single-node deployments and testing.
 */
@Slf4j
public class InMemoryAsyncStepStateRepository implements AsyncStepStateRepository {
    
    private final Map<String, AsyncStepState> storage = new ConcurrentHashMap<>();
    
    @Override
    public AsyncStepState save(AsyncStepState state) {
        if (state == null || state.getMessageId() == null) {
            throw new IllegalArgumentException("State and messageId cannot be null");
        }
        
        storage.put(state.getMessageId(), state);
        log.debug("Saved async step state for messageId: {}", state.getMessageId());
        return state;
    }
    
    @Override
    public Optional<AsyncStepState> findByMessageId(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        
        return Optional.ofNullable(storage.get(messageId));
    }
    
    @Override
    public void deleteByMessageId(String messageId) {
        if (messageId != null) {
            storage.remove(messageId);
            log.debug("Deleted async step state for messageId: {}", messageId);
        }
    }
    
    @Override
    public boolean existsByMessageId(String messageId) {
        return messageId != null && storage.containsKey(messageId);
    }
    
    @Override
    public int deleteOlderThan(long timestampMillis) {
        int deletedCount = 0;
        
        for (Map.Entry<String, AsyncStepState> entry : storage.entrySet()) {
            AsyncStepState state = entry.getValue();
            if (state.getStartTime() < timestampMillis) {
                storage.remove(entry.getKey());
                deletedCount++;
            }
        }
        
        log.debug("Deleted {} async step states older than {}", deletedCount, timestampMillis);
        return deletedCount;
    }
    
    @Override
    public boolean updateProgress(String messageId, int percentComplete, String statusMessage) {
        if (messageId == null) {
            return false;
        }
        
        AsyncStepState state = storage.get(messageId);
        if (state == null) {
            return false;
        }
        
        state.updateProgress(percentComplete, statusMessage);
        log.debug("Updated progress for messageId: {} to {}% - {}", messageId, percentComplete, statusMessage);
        return true;
    }
}