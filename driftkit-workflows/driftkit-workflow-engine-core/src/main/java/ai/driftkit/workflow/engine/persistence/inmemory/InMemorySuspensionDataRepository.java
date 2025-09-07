package ai.driftkit.workflow.engine.persistence.inmemory;

import ai.driftkit.workflow.engine.domain.SuspensionData;
import ai.driftkit.workflow.engine.persistence.SuspensionDataRepository;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of SuspensionDataRepository.
 */
@Slf4j
public class InMemorySuspensionDataRepository implements SuspensionDataRepository {
    
    private final Map<String, SuspensionData> suspensionDataByInstanceId = new ConcurrentHashMap<>();
    private final Map<String, String> instanceIdByMessageId = new ConcurrentHashMap<>();
    
    @Override
    public void save(String instanceId, SuspensionData suspensionData) {
        if (instanceId == null || suspensionData == null) {
            throw new IllegalArgumentException("Instance ID and suspension data cannot be null");
        }
        
        suspensionDataByInstanceId.put(instanceId, suspensionData);
        
        // Also index by message ID for quick lookup
        if (suspensionData.messageId() != null) {
            instanceIdByMessageId.put(suspensionData.messageId(), instanceId);
        }
        
        log.debug("Saved suspension data for instance: {}", instanceId);
    }
    
    @Override
    public Optional<SuspensionData> findByInstanceId(String instanceId) {
        if (instanceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(suspensionDataByInstanceId.get(instanceId));
    }
    
    @Override
    public Optional<SuspensionData> findByMessageId(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        
        String instanceId = instanceIdByMessageId.get(messageId);
        if (instanceId == null) {
            return Optional.empty();
        }
        
        return findByInstanceId(instanceId);
    }
    
    /**
     * Get instance ID by message ID for reverse lookup.
     */
    public Optional<String> getInstanceIdByMessageId(String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(instanceIdByMessageId.get(messageId));
    }
    
    @Override
    public void deleteByInstanceId(String instanceId) {
        if (instanceId == null) {
            return;
        }
        
        SuspensionData removed = suspensionDataByInstanceId.remove(instanceId);
        if (removed != null && removed.messageId() != null) {
            instanceIdByMessageId.remove(removed.messageId());
            log.debug("Deleted suspension data for instance: {}", instanceId);
        }
    }
}