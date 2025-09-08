package ai.driftkit.workflow.engine.spring.client;

import ai.driftkit.common.domain.PromptRequest;
import ai.driftkit.workflow.engine.spring.dto.ModelRequestDtos.TextRequest;
import ai.driftkit.workflow.engine.agent.AgentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for ModelRequestController.
 * Provides remote access to synchronous LLM model request endpoints.
 */
@FeignClient(name = "model-request-service", path = "/api/v1/model", configuration = WorkflowFeignConfiguration.class)
public interface ModelRequestClient {
    
    /**
     * Process a prompt request synchronously.
     */
    @PostMapping(value = "/prompt", produces = MediaType.APPLICATION_JSON_VALUE)
    AgentResponse<?> processPromptRequest(@RequestBody PromptRequest request);
    
    /**
     * Process a text request synchronously.
     */
    @PostMapping(value = "/text", produces = MediaType.APPLICATION_JSON_VALUE)
    AgentResponse<String> processTextRequest(@RequestBody TextRequest request);
}