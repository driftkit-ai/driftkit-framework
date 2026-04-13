package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.PromptMethodConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PromptMethodConfigRepository extends MongoRepository<PromptMethodConfig, String> {
}
