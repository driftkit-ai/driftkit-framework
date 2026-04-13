package ai.driftkit.clients.deepseek.client;

import ai.driftkit.clients.deepseek.domain.DeepSeekChatCompletionRequest;
import ai.driftkit.clients.deepseek.domain.DeepSeekChatCompletionResponse;
import feign.Headers;
import feign.RequestLine;

public interface DeepSeekApiClient {

    @RequestLine("POST /chat/completions")
    @Headers("Content-Type: application/json")
    DeepSeekChatCompletionResponse createChatCompletion(DeepSeekChatCompletionRequest request);
}
