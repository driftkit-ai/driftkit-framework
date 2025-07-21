package ai.driftkit.workflows.spring.repository;

import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelRequestTraceRepository extends MongoRepository<ModelRequestTrace, String> {
    List<ModelRequestTrace> findByContextId(String contextId);
    List<ModelRequestTrace> findByWorkflowInfoWorkflowId(String workflowId);
    Page<ModelRequestTrace> findByModelId(String modelId, Pageable pageable);
}