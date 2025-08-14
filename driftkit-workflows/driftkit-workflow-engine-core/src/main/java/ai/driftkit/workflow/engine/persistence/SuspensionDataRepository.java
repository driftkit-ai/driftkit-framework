package ai.driftkit.workflow.engine.persistence;

import ai.driftkit.workflow.engine.domain.SuspensionData;
import java.util.Optional;

/**
 * Repository for managing workflow suspension data separately from WorkflowInstance.
 * This allows workflows to be suspended without polluting the main instance state.
 */
public interface SuspensionDataRepository {
    
    /**
     * Save suspension data for a workflow instance.
     * 
     * @param instanceId The workflow instance ID
     * @param suspensionData The suspension data to save
     */
    void save(String instanceId, SuspensionData suspensionData);
    
    /**
     * Find suspension data by workflow instance ID.
     * 
     * @param instanceId The workflow instance ID
     * @return Optional containing the suspension data if found
     */
    Optional<SuspensionData> findByInstanceId(String instanceId);
    
    /**
     * Find suspension data by message ID.
     * 
     * @param messageId The message ID associated with the suspension
     * @return Optional containing the suspension data if found
     */
    Optional<SuspensionData> findByMessageId(String messageId);
    
    /**
     * Delete suspension data for a workflow instance.
     * 
     * @param instanceId The workflow instance ID
     */
    void deleteByInstanceId(String instanceId);
}