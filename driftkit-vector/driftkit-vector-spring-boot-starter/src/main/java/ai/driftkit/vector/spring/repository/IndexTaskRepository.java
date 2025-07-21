package ai.driftkit.vector.spring.repository;

import ai.driftkit.vector.spring.domain.IndexTask;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexTaskRepository extends MongoRepository<IndexTask, String> {
}