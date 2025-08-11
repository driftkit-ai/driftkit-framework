package ai.driftkit.workflow.engine.persistence;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * In-memory implementation of WorkflowStateRepository.
 * Suitable for testing and development, or for production use cases
 * where persistence across restarts is not required.
 * 
 * <p>This implementation is thread-safe and uses concurrent data structures
 * with read-write locks for optimal performance.</p>
 */
@Slf4j
public class InMemoryWorkflowStateRepository implements WorkflowStateRepository {
    
    private final Map<String, WorkflowInstance> instances = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    /**
     * Maximum number of instances to keep in memory.
     * Older completed instances will be evicted when this limit is reached.
     */
    private final int maxInstances;
    
    /**
     * Creates a repository with default settings (max 10,000 instances).
     */
    public InMemoryWorkflowStateRepository() {
        this(10_000);
    }
    
    /**
     * Creates a repository with a specified maximum capacity.
     * 
     * @param maxInstances Maximum number of instances to keep in memory
     */
    public InMemoryWorkflowStateRepository(int maxInstances) {
        if (maxInstances <= 0) {
            throw new IllegalArgumentException("Max instances must be positive");
        }
        this.maxInstances = maxInstances;
        log.info("Created in-memory workflow repository with max capacity: {}", maxInstances);
    }
    
    @Override
    public void save(WorkflowInstance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null");
        }
        
        if (instance.getInstanceId() == null || instance.getInstanceId().isBlank()) {
            throw new IllegalArgumentException("Instance ID cannot be null or blank");
        }
        
        lock.writeLock().lock();
        try {
            // Check capacity and evict if necessary
            if (instances.size() >= maxInstances && !instances.containsKey(instance.getInstanceId())) {
                evictOldestCompleted();
            }
            
            // Clone the instance to prevent external modifications
            WorkflowInstance cloned = cloneInstance(instance);
            instances.put(instance.getInstanceId(), cloned);
            
            log.debug("Saved workflow instance: {} (status: {})", 
                instance.getInstanceId(), instance.getStatus());
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Optional<WorkflowInstance> load(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return Optional.empty();
        }
        
        lock.readLock().lock();
        try {
            WorkflowInstance instance = instances.get(instanceId);
            // Return a clone to prevent external modifications
            return Optional.ofNullable(instance).map(this::cloneInstance);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean delete(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return false;
        }
        
        lock.writeLock().lock();
        try {
            WorkflowInstance removed = instances.remove(instanceId);
            if (removed != null) {
                log.debug("Deleted workflow instance: {}", instanceId);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public List<WorkflowInstance> findByStatus(WorkflowInstance.WorkflowStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        
        lock.readLock().lock();
        try {
            return instances.values().stream()
                .filter(instance -> instance.getStatus() == status)
                .map(this::cloneInstance)
                .sorted(Comparator.comparing(WorkflowInstance::getCreatedAt).reversed())
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public List<WorkflowInstance> findByWorkflowId(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return Collections.emptyList();
        }
        
        lock.readLock().lock();
        try {
            return instances.values().stream()
                .filter(instance -> workflowId.equals(instance.getWorkflowId()))
                .map(this::cloneInstance)
                .sorted(Comparator.comparing(WorkflowInstance::getCreatedAt).reversed())
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public List<WorkflowInstance> findByWorkflowIdAndStatus(String workflowId, 
                                                            WorkflowInstance.WorkflowStatus status) {
        if (workflowId == null || workflowId.isBlank() || status == null) {
            return Collections.emptyList();
        }
        
        lock.readLock().lock();
        try {
            return instances.values().stream()
                .filter(instance -> workflowId.equals(instance.getWorkflowId()) && 
                                   instance.getStatus() == status)
                .map(this::cloneInstance)
                .sorted(Comparator.comparing(WorkflowInstance::getCreatedAt).reversed())
                .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public long countByStatus(WorkflowInstance.WorkflowStatus status) {
        if (status == null) {
            return 0;
        }
        
        lock.readLock().lock();
        try {
            return instances.values().stream()
                .filter(instance -> instance.getStatus() == status)
                .count();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public int deleteCompletedOlderThan(int ageInDays) {
        if (ageInDays < 0) {
            throw new IllegalArgumentException("Age in days must be non-negative");
        }
        
        Instant cutoffTime = Instant.now().minus(ageInDays, ChronoUnit.DAYS);
        
        lock.writeLock().lock();
        try {
            List<String> toDelete = instances.entrySet().stream()
                .filter(entry -> {
                    WorkflowInstance instance = entry.getValue();
                    return instance.isTerminal() && 
                           instance.getCompletedAt() != null &&
                           instance.getCompletedAt().isBefore(cutoffTime);
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            toDelete.forEach(instances::remove);
            
            if (!toDelete.isEmpty()) {
                log.info("Deleted {} completed workflow instances older than {} days", 
                    toDelete.size(), ageInDays);
            }
            
            return toDelete.size();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets the current number of stored instances.
     * 
     * @return The number of instances in memory
     */
    public int size() {
        lock.readLock().lock();
        try {
            return instances.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clears all instances from memory.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            int count = instances.size();
            instances.clear();
            log.info("Cleared {} workflow instances from memory", count);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Evicts the oldest completed instance to make room for new ones.
     * Called internally when capacity is reached.
     */
    private void evictOldestCompleted() {
        Optional<Map.Entry<String, WorkflowInstance>> oldest = instances.entrySet().stream()
            .filter(entry -> entry.getValue().isTerminal())
            .min(Comparator.comparing(entry -> entry.getValue().getCompletedAt()));
        
        if (oldest.isPresent()) {
            instances.remove(oldest.get().getKey());
            log.debug("Evicted oldest completed instance: {} to maintain capacity", 
                oldest.get().getKey());
        } else {
            // No completed instances to evict, remove oldest running/suspended
            Optional<Map.Entry<String, WorkflowInstance>> oldestAny = instances.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().getCreatedAt()));
            
            oldestAny.ifPresent(entry -> {
                instances.remove(entry.getKey());
                log.warn("Evicted non-completed instance: {} to maintain capacity", entry.getKey());
            });
        }
    }
    
    /**
     * Creates a deep clone of a workflow instance to prevent external modifications.
     * 
     * @param instance The instance to clone
     * @return A deep copy of the instance
     */
    private WorkflowInstance cloneInstance(WorkflowInstance instance) {
        // For a production implementation, consider using a proper deep cloning library
        // or implement proper clone methods on all nested objects
        return WorkflowInstance.builder()
            .instanceId(instance.getInstanceId())
            .workflowId(instance.getWorkflowId())
            .workflowVersion(instance.getWorkflowVersion())
            .context(instance.getContext()) // Note: WorkflowContext is immutable
            .status(instance.getStatus())
            .currentStepId(instance.getCurrentStepId())
            .nextStepId(instance.getNextStepId())
            .createdAt(instance.getCreatedAt())
            .updatedAt(instance.getUpdatedAt())
            .completedAt(instance.getCompletedAt())
            .executionHistory(new ArrayList<>(instance.getExecutionHistory()))
            .metadata(new ConcurrentHashMap<>(instance.getMetadata()))
            .errorInfo(instance.getErrorInfo())
            .suspensionData(instance.getSuspensionData())
            .asyncStepStates(new ConcurrentHashMap<>(instance.getAsyncStepStates()))
            .build();
    }
}