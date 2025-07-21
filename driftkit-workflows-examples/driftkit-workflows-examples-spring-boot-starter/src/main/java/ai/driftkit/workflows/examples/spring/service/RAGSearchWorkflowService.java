package ai.driftkit.workflows.examples.spring.service;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.examples.workflows.RAGSearchWorkflow;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import org.springframework.stereotype.Service;

/**
 * Spring wrapper for the framework-agnostic RAGSearchWorkflow.
 * This class handles Spring dependency injection and delegates to the core workflow.
 */
@Service
public class RAGSearchWorkflowService extends RAGSearchWorkflow {

    public RAGSearchWorkflowService(
            EtlConfig config,
            PromptService promptService,
            ModelRequestService modelRequestService
    ) throws Exception {
        super(config, promptService, modelRequestService);
    }
}