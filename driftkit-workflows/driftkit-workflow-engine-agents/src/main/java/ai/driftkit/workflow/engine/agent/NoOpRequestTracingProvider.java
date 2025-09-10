package ai.driftkit.workflow.engine.agent;

import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.ModelImageRequest;
import ai.driftkit.common.domain.client.ModelImageResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * No-operation implementation of RequestTracingProvider.
 * Used as a fallback when tracing is disabled or MongoDB is not available.
 */
@Slf4j
public class NoOpRequestTracingProvider implements RequestTracingProvider {
    
    @Override
    public void traceTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        log.debug("Text request tracing disabled - NoOp provider active");
    }
    
    @Override
    public void traceImageRequest(ModelImageRequest request, ModelImageResponse response, RequestContext context) {
        log.debug("Image request tracing disabled - NoOp provider active");
    }
    
    @Override
    public void traceImageToTextRequest(ModelTextRequest request, ModelTextResponse response, RequestContext context) {
        log.debug("Image-to-text request tracing disabled - NoOp provider active");
    }
}