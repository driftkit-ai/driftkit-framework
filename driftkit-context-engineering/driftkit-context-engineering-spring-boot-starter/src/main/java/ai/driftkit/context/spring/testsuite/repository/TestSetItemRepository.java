package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.archive.TestSetItemImpl;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestSetItemRepository extends MongoRepository<TestSetItemImpl, String> {
    List<TestSetItemImpl> findByTestSetId(String testSetId);
    List<TestSetItemImpl> findByTestSetIdOrderByCreatedAtDesc(String testSetId);
    void deleteByTestSetId(String testSetId);
}