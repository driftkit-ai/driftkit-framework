package ai.driftkit.workflow.engine.spring.tracing.repository;

import ai.driftkit.workflow.engine.spring.tracing.domain.ModelRequestTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ModelRequestTrace entities.
 * Provides persistence and query capabilities for model request traces.
 */
@Repository
public interface CoreModelRequestTraceRepository extends MongoRepository<ModelRequestTrace, String> {
    
    /**
     * Find traces by context ID
     */
    List<ModelRequestTrace> findByContextId(String contextId);
    
    /**
     * Find traces by context ID with pagination
     */
    Page<ModelRequestTrace> findByContextId(String contextId, Pageable pageable);
    
    /**
     * Find traces by chat ID
     */
    List<ModelRequestTrace> findByChatId(String chatId);
    
    /**
     * Find traces by chat ID with pagination
     */
    Page<ModelRequestTrace> findByChatId(String chatId, Pageable pageable);
    
    /**
     * Find traces by workflow ID
     */
    @Query("{ 'workflowInfo.workflowId': ?0 }")
    List<ModelRequestTrace> findByWorkflowId(String workflowId);
    
    /**
     * Find traces by workflow ID with pagination
     */
    @Query("{ 'workflowInfo.workflowId': ?0 }")
    Page<ModelRequestTrace> findByWorkflowId(String workflowId, Pageable pageable);
    
    /**
     * Find traces by context type
     */
    List<ModelRequestTrace> findByContextType(ModelRequestTrace.ContextType contextType);
    
    /**
     * Find traces by context type with pagination
     */
    Page<ModelRequestTrace> findByContextType(ModelRequestTrace.ContextType contextType, Pageable pageable);
    
    /**
     * Find traces by request type
     */
    List<ModelRequestTrace> findByRequestType(ModelRequestTrace.RequestType requestType);
    
    /**
     * Find traces by user ID
     */
    List<ModelRequestTrace> findByUserId(String userId);
    
    /**
     * Find traces with errors
     */
    @Query("{ 'errorMessage': { $exists: true, $ne: null } }")
    List<ModelRequestTrace> findTracesWithErrors();
    
    /**
     * Find traces within time range
     */
    @Query("{ 'timestamp': { $gte: ?0, $lte: ?1 } }")
    List<ModelRequestTrace> findByTimestampBetween(long startTime, long endTime);
    
    /**
     * Count traces by context type
     */
    long countByContextType(ModelRequestTrace.ContextType contextType);
    
    /**
     * Count traces by request type
     */
    long countByRequestType(ModelRequestTrace.RequestType requestType);
}