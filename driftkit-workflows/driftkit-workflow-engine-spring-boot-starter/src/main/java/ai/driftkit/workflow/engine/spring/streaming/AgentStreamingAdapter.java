package ai.driftkit.workflow.engine.spring.streaming;

import ai.driftkit.common.domain.streaming.StreamingCallback;
import ai.driftkit.workflow.engine.agent.Agent;
import ai.driftkit.workflow.engine.agent.LLMAgent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring adapter for Agent streaming operations.
 * Provides utilities to convert Agent streaming responses to Spring WebFlux Flux.
 */
public class AgentStreamingAdapter {
    
    /**
     * Execute an agent with streaming and convert to Flux.
     * Only works with LLMAgent that supports streaming.
     * 
     * @param agent The agent to execute (must be LLMAgent for streaming support)
     * @param input The input text
     * @return A Flux that emits response tokens
     */
    public static Flux<String> executeStreaming(Agent agent, String input) {
        return executeStreaming(agent, input, null);
    }
    
    /**
     * Execute an agent with streaming and variables, converting to Flux.
     * Only works with LLMAgent that supports streaming.
     * 
     * @param agent The agent to execute (must be LLMAgent for streaming support)
     * @param input The input text
     * @param variables Context variables
     * @return A Flux that emits response tokens
     */
    public static Flux<String> executeStreaming(Agent agent, String input, Map<String, Object> variables) {
        if (!(agent instanceof LLMAgent)) {
            // For non-LLMAgent, just return the result as a single-item Flux
            String result = (variables != null) ? agent.execute(input, variables) : agent.execute(input);
            return Flux.just(result);
        }
        
        LLMAgent llmAgent = (LLMAgent) agent;
        
        return Flux.create(sink -> {
            AtomicBoolean cancelled = new AtomicBoolean(false);
            
            // Create callback that bridges to Flux sink
            StreamingCallback<String> callback = new StreamingCallback<String>() {
                @Override
                public void onNext(String item) {
                    if (!cancelled.get() && !sink.isCancelled()) {
                        sink.next(item);
                    }
                }
                
                @Override
                public void onError(Throwable error) {
                    if (!cancelled.get() && !sink.isCancelled()) {
                        sink.error(error);
                    }
                }
                
                @Override
                public void onComplete() {
                    if (!cancelled.get() && !sink.isCancelled()) {
                        sink.complete();
                    }
                }
            };
            
            // Execute streaming with callback
            CompletableFuture<String> future = llmAgent.executeStreaming(input, variables, callback);
            
            // Handle cancellation
            sink.onDispose(() -> {
                cancelled.set(true);
                future.cancel(true);
            });
            
        }, FluxSink.OverflowStrategy.BUFFER);
    }
}