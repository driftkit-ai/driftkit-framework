package ai.driftkit.clients.claude.client;

import ai.driftkit.clients.claude.domain.ClaudeMessageRequest;
import ai.driftkit.clients.claude.domain.ClaudeMessageResponse;
import feign.Headers;
import feign.RequestLine;

public interface ClaudeApiClient {
    
    @RequestLine("POST /v1/messages")
    @Headers({
        "Content-Type: application/json",
        "Accept: application/json",
        "anthropic-version: 2023-06-01",
        "anthropic-beta: structured-outputs-2025-11-13"
    })
    ClaudeMessageResponse createMessage(ClaudeMessageRequest request);
    
    // TODO: Add streaming support when needed
    // @RequestLine("POST /v1/messages")
    // @Headers({
    //     "Content-Type: application/json",
    //     "Accept: text/event-stream",
    //     "anthropic-version: 2023-06-01"
    // })
    // Stream<ClaudeStreamEvent> createMessageStream(ClaudeMessageRequest request);
}