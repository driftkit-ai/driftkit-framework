package ai.driftkit.workflows.spring.repository;

import ai.driftkit.workflows.spring.domain.ChecklistItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChecklistItemRepository extends MongoRepository<ChecklistItemEntity, String> {
    
    /**
     * Find all checklist items by promptId with pagination
     */
    List<ChecklistItemEntity> findByPromptId(String promptId, Pageable pageable);
    
    /**
     * Find all checklist items by query with pagination
     */
    List<ChecklistItemEntity> findByQuery(String query, Pageable pageable);
    
    /**
     * Find all checklist items by query containing with pagination
     */
    List<ChecklistItemEntity> findByQueryContaining(String queryPart, Pageable pageable);
    
    /**
     * Find all checklist items by promptId or query with pagination
     */
    List<ChecklistItemEntity> findByPromptIdOrQuery(String promptId, String query, Pageable pageable);
    
    /**
     * Find checklist items by workflow type with pagination
     */
    List<ChecklistItemEntity> findByWorkflowType(String workflowType, Pageable pageable);
    
    /**
     * Find checklist items by severity with pagination
     */
    List<ChecklistItemEntity> findBySeverity(String severity, Pageable pageable);
    
    /**
     * Find checklist items by description containing with pagination
     */
    List<ChecklistItemEntity> findByDescriptionContaining(String description, Pageable pageable);
    
    /**
     * Find checklist items by normalized description with pagination
     */
    List<ChecklistItemEntity> findByNormalizedDescription(String normalizedDescription, Pageable pageable);
    
    /**
     * Find checklist items by promptId and workflowType with pagination
     */
    List<ChecklistItemEntity> findByPromptIdAndWorkflowType(String promptId, String workflowType, Pageable pageable);
    
    /**
     * Find checklist items that have not been marked as similar to others with pagination
     */
    List<ChecklistItemEntity> findBySimilarToIdIsNull(Pageable pageable);
    
    /**
     * Find checklist items that have been marked as similar to others with pagination
     */
    List<ChecklistItemEntity> findBySimilarToIdIsNotNull(Pageable pageable);
    
    /**
     * Find checklist items that reference a specific item as similar
     */
    List<ChecklistItemEntity> findBySimilarToId(String similarToId, Pageable pageable);
    
    /**
     * Find all unique non-null promptIds
     */
    @Query(value = "{ 'promptId': { $ne: null } }", fields = "{ '_id': 0, 'promptId': 1 }")
    List<String> findDistinctPromptIds();
}