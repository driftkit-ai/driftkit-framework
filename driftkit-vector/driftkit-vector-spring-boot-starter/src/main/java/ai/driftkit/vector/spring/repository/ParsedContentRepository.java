package ai.driftkit.vector.spring.repository;

import ai.driftkit.vector.spring.domain.ParsedContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParsedContentRepository extends MongoRepository<ParsedContent, String> {
}
