package ai.driftkit.vector.spring.repository;

import ai.driftkit.vector.spring.domain.Index;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndexRepository extends MongoRepository<Index, String> {
}