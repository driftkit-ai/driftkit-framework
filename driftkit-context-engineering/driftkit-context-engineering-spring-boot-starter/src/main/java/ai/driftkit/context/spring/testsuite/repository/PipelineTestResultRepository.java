package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.PipelineTestResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PipelineTestResultRepository extends MongoRepository<PipelineTestResult, String> {
    List<PipelineTestResult> findByRunId(String runId);
}
