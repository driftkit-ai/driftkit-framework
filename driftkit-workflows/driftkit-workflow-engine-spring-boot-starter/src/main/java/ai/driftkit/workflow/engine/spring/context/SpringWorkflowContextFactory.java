package ai.driftkit.workflow.engine.spring.context;

import ai.driftkit.workflow.engine.core.WorkflowContext;
import ai.driftkit.workflow.engine.core.WorkflowContextFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Spring implementation of WorkflowContextFactory.
 * Provides Spring-aware context creation for workflows.
 */
@Slf4j
@Component
public class SpringWorkflowContextFactory implements WorkflowContextFactory {
    
    @Override
    public WorkflowContext create(String runId, Object triggerData, String instanceId) {
        WorkflowContext context = new WorkflowContext(runId, triggerData, instanceId);
        log.debug("Created WorkflowContext for run: {}", runId);
        return context;
    }
}