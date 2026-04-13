package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageResponse;
import ai.driftkit.common.domain.client.ModelContentMessage;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Interface for providing request tracing capabilities to LLMAgent.
 * Implementations can provide tracing via Spring (ModelRequestService),
 * REST API, or other mechanisms.
 */
public interface RequestTracingProvider {

    /**
     * Trace a text-to-text request and response
     */
    void traceTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context);

    /**
     * Trace a text-to-image request and response
     */
    void traceImageRequest(ModelImageRequest request, ModelImageResponse response, RequestContext context);

    /**
     * Trace an image-to-text request and response
     */
    void traceImageToTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context);

    /**
     * Context information for request tracing
     */
    @Data
    @Builder
    class RequestContext {
        private final String contextId;
        private final String contextType;
        private final String promptId;
        private final Map<String, Object> variables;
        private final String workflowId;
        private final String workflowType;
        private final String workflowStep;
        private final String chatId;
        private final String purpose;
        private final String systemMessage;
        private final Map<String, String> messageProperties;
        private final List<ModelContentMessage> messages;
    }
}