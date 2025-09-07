package ai.driftkit.workflow.engine.spring.streaming;

import ai.driftkit.common.domain.streaming.StreamingResponse;
import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.workflow.engine.agent.Agent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Map;

/**
 * Spring adapter for Agent streaming operations.
 * Provides utilities to convert Agent streaming responses to Spring WebFlux Flux.
 */
public class AgentStreamingAdapter {
    
    /**
     * Execute an agent with streaming and convert to Flux.
     * 
     * @param agent The agent to execute
     * @param input The input text
     * @return A Flux that emits response tokens
     */
    public static Flux<String> executeStreaming(Agent agent, String input) {
        StreamingResponse<String> streamingResponse = agent.executeStreaming(input);
        return toFlux(streamingResponse);
    }
    
    /**
     * Execute an agent with streaming and variables, converting to Flux.
     * 
     * @param agent The agent to execute
     * @param input The input text
     * @param variables Context variables
     * @return A Flux that emits response tokens
     */
    public static Flux<String> executeStreaming(Agent agent, String input, Map<String, Object> variables) {
        StreamingResponse<String> streamingResponse = agent.executeStreaming(input, variables);
        return toFlux(streamingResponse);
    }
    
    /**
     * Convert a StreamingResponse to a Flux.
     * 
     * @param streamingResponse The streaming response from the agent
     * @return A Flux that emits the same items
     */
    private static Flux<String> toFlux(StreamingResponse<String> streamingResponse) {
        return Flux.create(sink -> {
            streamingResponse.subscribe(new StreamingCallback<String>() {
                @Override
                public void onNext(String item) {
                    if (!sink.isCancelled()) {
                        sink.next(item);
                    }
                }
                
                @Override
                public void onError(Throwable error) {
                    if (!sink.isCancelled()) {
                        sink.error(error);
                    }
                }
                
                @Override
                public void onComplete() {
                    if (!sink.isCancelled()) {
                        sink.complete();
                    }
                }
            });
            
            // Handle cancellation
            sink.onDispose(() -> streamingResponse.cancel());
        }, FluxSink.OverflowStrategy.BUFFER);
    }
}