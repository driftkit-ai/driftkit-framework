package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.PromptAudit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PromptAuditRepository extends MongoRepository<PromptAudit, String> {
    List<PromptAudit> findByPromptMethodOrderByTimestampDesc(String promptMethod);
}
