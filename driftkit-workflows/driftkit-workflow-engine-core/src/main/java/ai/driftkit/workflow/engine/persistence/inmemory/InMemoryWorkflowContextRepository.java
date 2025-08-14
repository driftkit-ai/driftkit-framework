package ai.driftkit.workflow.engine.persistence.inmemory;

import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.persistence.WorkflowContextRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of WorkflowContextRepository.
 * This implementation stores workflow contexts in memory using a ConcurrentHashMap.
 * Suitable for single-instance deployments and testing.
 */
@Slf4j
public class InMemoryWorkflowContextRepository implements WorkflowContextRepository {
    
    private final Map<String, WorkflowContext> contexts = new ConcurrentHashMap<>();
    
    @Override
    public WorkflowContext save(WorkflowContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        
        String instanceId = context.getInstanceId();
        if (StringUtils.isBlank(instanceId)) {
            throw new IllegalArgumentException("Instance ID cannot be null or blank");
        }
        
        // Create a copy to prevent external modifications
        WorkflowContext copy = WorkflowContext.fromExisting(
            context.getRunId(),
            context.getTriggerData(),
            context.getStepOutputs(),
            context.getCustomData(),
            instanceId
        );
        
        contexts.put(instanceId, copy);
        log.debug("Saved workflow context for instance: {}", instanceId);
        
        return copy;
    }
    
    @Override
    public Optional<WorkflowContext> findByInstanceId(String instanceId) {
        if (StringUtils.isBlank(instanceId)) {
            return Optional.empty();
        }
        
        WorkflowContext context = contexts.get(instanceId);
        if (context == null) {
            return Optional.empty();
        }
        
        // Return a copy to prevent external modifications
        WorkflowContext copy = WorkflowContext.fromExisting(
            context.getRunId(),
            context.getTriggerData(),
            context.getStepOutputs(),
            context.getCustomData(),
            context.getInstanceId()
        );
        
        return Optional.of(copy);
    }
    
    @Override
    public boolean deleteByInstanceId(String instanceId) {
        if (StringUtils.isBlank(instanceId)) {
            return false;
        }
        
        WorkflowContext removed = contexts.remove(instanceId);
        if (removed != null) {
            log.debug("Deleted workflow context for instance: {}", instanceId);
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean existsByInstanceId(String instanceId) {
        if (StringUtils.isBlank(instanceId)) {
            return false;
        }
        
        return contexts.containsKey(instanceId);
    }
    
    /**
     * Gets the current number of stored contexts.
     * Useful for monitoring and testing.
     * 
     * @return The number of contexts in memory
     */
    public int size() {
        return contexts.size();
    }
    
    /**
     * Clears all stored contexts.
     * Useful for testing.
     */
    public void clear() {
        contexts.clear();
        log.debug("Cleared all workflow contexts");
    }
}