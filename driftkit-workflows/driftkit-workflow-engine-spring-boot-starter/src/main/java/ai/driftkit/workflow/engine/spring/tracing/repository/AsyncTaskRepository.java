package ai.driftkit.workflow.engine.spring.tracing.repository;

import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity;
import ai.driftkit.workflow.engine.spring.tracing.domain.AsyncTaskEntity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for async task entities.
 * Provides persistence and query operations for async LLM task execution.
 */
@Repository
public interface AsyncTaskRepository extends MongoRepository<AsyncTaskEntity, String> {
    
    /**
     * Find task by ID
     */
    Optional<AsyncTaskEntity> findByTaskId(String taskId);
    
    /**
     * Find tasks by chat ID
     */
    List<AsyncTaskEntity> findByChatIdOrderByCreatedAtDesc(String chatId);
    
    /**
     * Find tasks by user ID
     */
    Page<AsyncTaskEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * Find tasks by status
     */
    List<AsyncTaskEntity> findByStatus(TaskStatus status);
    
    /**
     * Find tasks by status with pagination
     */
    Page<AsyncTaskEntity> findByStatus(TaskStatus status, Pageable pageable);
    
    /**
     * Find running tasks older than specified timestamp
     */
    @Query("{ 'status': 'RUNNING', 'startedAt': { $lt: ?0 } }")
    List<AsyncTaskEntity> findStaleRunningTasks(Long olderThan);
    
    /**
     * Find tasks by workflow ID
     */
    List<AsyncTaskEntity> findByWorkflowIdOrderByCreatedAtDesc(String workflowId);
    
    /**
     * Count tasks by status
     */
    Long countByStatus(TaskStatus status);
    
    /**
     * Find tasks by user and status
     */
    List<AsyncTaskEntity> findByUserIdAndStatus(String userId, TaskStatus status);
    
    /**
     * Find tasks by list of task IDs
     */
    List<AsyncTaskEntity> findByTaskIdIn(List<String> taskIds);
}