package ai.driftkit.workflow.controllers.controller;

import ai.driftkit.common.domain.PromptRequest;
import ai.driftkit.workflow.engine.agent.AgentResponse;
import ai.driftkit.workflow.controllers.service.AsyncTaskService;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.TextRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for direct LLM model requests outside of workflows.
 * Delegates all execution logic to AsyncTaskService for consistency.
 * Supports text, image generation, and multimodal requests.
 * Only activated when AsyncTaskService is available (requires MongoDB).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/model")
@RequiredArgsConstructor
@ConditionalOnWebApplication
public class ModelRequestController {

    @Autowired(required = false)
    private AsyncTaskService asyncTaskService;
    
    /**
     * Process a prompt request - supports text, image generation, and multimodal.
     */
    @PostMapping(value = "/prompt", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentResponse<?> processPromptRequest(@RequestBody PromptRequest request) {
        log.debug("Processing prompt request");
        // Delegate all logic to AsyncTaskService
        return asyncTaskService.executePromptRequestSync(request);
    }
    
    /**
     * Process a direct text request without promptId.
     */
    @PostMapping(value = "/text", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public AgentResponse<?> processTextRequest(@RequestBody TextRequest request) {
        log.debug("Processing direct text request");
        // Delegate all logic to AsyncTaskService
        return asyncTaskService.executeTextRequestSync(request);
    }
}