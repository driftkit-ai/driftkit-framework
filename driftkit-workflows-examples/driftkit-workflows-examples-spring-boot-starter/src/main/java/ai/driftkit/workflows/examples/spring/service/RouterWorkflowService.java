package ai.driftkit.workflows.examples.spring.service;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.workflows.examples.workflows.RouterWorkflow;
import ai.driftkit.workflows.examples.workflows.RAGSearchWorkflow;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Spring wrapper for the framework-agnostic RouterWorkflow.
 * This class handles Spring dependency injection and delegates to the core workflow.
 */
@Service
public class RouterWorkflowService extends RouterWorkflow {

    public RouterWorkflowService(
            EtlConfig config,
            PromptService promptService,
            RAGSearchWorkflow searchWorkflow,
            ModelRequestService modelRequestService
    ) throws IOException {
        super(config, promptService, searchWorkflow, modelRequestService);
    }
}