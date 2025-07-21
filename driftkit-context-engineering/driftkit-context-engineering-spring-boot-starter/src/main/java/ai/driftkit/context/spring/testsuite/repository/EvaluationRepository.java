package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.Evaluation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationRepository extends MongoRepository<Evaluation, String> {
    
    /**
     * Find all evaluations for a test set
     */
    List<Evaluation> findByTestSetId(String testSetId);
    
    /**
     * Find all global evaluations (where testSetId is null)
     */
    List<Evaluation> findByTestSetIdIsNull();
    
    /**
     * Delete all evaluations for a test set
     */
    void deleteByTestSetId(String testSetId);
}