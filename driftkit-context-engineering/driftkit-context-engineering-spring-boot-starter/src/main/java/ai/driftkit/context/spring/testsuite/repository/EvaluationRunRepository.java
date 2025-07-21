package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.EvaluationRun;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationRunRepository extends MongoRepository<EvaluationRun, String> {
    
    /**
     * Find all runs for a test set
     */
    List<EvaluationRun> findByTestSetId(String testSetId);
    
    /**
     * Find all runs with a specific status
     */
    List<EvaluationRun> findByStatus(EvaluationRun.RunStatus status);
    
    /**
     * Delete all runs for a test set
     */
    void deleteByTestSetId(String testSetId);
}