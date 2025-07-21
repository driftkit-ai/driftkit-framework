package ai.driftkit.workflows.examples.spring.service;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.vector.spring.service.IndexService;
import ai.driftkit.workflows.examples.workflows.ChatWorkflow;
import ai.driftkit.workflows.spring.service.ChatService;
import ai.driftkit.workflows.spring.service.ImageModelService;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import ai.driftkit.workflows.spring.service.TasksService;
import org.springframework.stereotype.Service;

/**
 * Spring wrapper for the framework-agnostic ChatWorkflow.
 * This class handles Spring dependency injection and delegates to the core workflow.
 */
@Service
public class ChatWorkflowService extends ChatWorkflow {

    public ChatWorkflowService(
            EtlConfig config,
            PromptService promptService,
            ModelRequestService modelRequestService,
            ChatService chatService,
            TasksService tasksService,
            ImageModelService imageService,
            IndexService indexService
    ) throws Exception {
        super(config, promptService, modelRequestService, chatService, tasksService, imageService, indexService);
    }
}