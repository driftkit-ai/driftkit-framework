package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.common.domain.Language;
import ai.driftkit.context.spring.testsuite.domain.PromptEnvironment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PromptEnvironmentRepository extends MongoRepository<PromptEnvironment, String> {
    List<PromptEnvironment> findByMethod(String method);
    Optional<PromptEnvironment> findByMethodAndLanguageAndEnvironment(String method, Language language, String environment);
}
