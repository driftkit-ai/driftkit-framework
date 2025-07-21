package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.EvaluationResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationResultRepository extends MongoRepository<EvaluationResult, String> {
    
    /**
     * Find all results for a specific evaluation
     */
    List<EvaluationResult> findByEvaluationId(String evaluationId);
    
    /**
     * Find all results for a specific test set item
     */
    List<EvaluationResult> findByTestSetItemId(String testSetItemId);
    
    /**
     * Find all results for a specific run
     */
    List<EvaluationResult> findByRunId(String runId);
    
    /**
     * Find all results for a specific evaluation and run
     */
    List<EvaluationResult> findByEvaluationIdAndRunId(String evaluationId, String runId);
    
    /**
     * Delete all results for a specific evaluation
     */
    void deleteByEvaluationId(String evaluationId);
    
    /**
     * Delete all results for a specific run
     */
    void deleteByRunId(String runId);
}