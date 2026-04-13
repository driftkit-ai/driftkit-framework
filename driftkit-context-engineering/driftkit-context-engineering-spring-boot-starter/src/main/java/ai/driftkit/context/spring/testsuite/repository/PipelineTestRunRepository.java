package ai.driftkit.context.spring.testsuite.repository;

import ai.driftkit.context.spring.testsuite.domain.PipelineTestRun;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PipelineTestRunRepository extends MongoRepository<PipelineTestRun, String> {
    List<PipelineTestRun> findByPipelineId(String pipelineId);
}
