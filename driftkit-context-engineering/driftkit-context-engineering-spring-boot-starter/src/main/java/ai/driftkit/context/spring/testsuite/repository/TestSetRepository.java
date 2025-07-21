package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.TestSet;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestSetRepository extends MongoRepository<TestSet, String> {
    List<TestSet> findAllByOrderByCreatedAtDesc();
    List<TestSet> findByFolderIdOrderByCreatedAtDesc(String folderId);
    List<TestSet> findByFolderIdIsNullOrderByCreatedAtDesc();
}