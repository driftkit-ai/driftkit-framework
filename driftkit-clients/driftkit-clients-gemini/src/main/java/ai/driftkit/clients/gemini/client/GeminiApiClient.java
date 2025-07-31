package ai.driftkit.clients.gemini.client;

import ai.driftkit.clients.gemini.domain.GeminiChatRequest;
import ai.driftkit.clients.gemini.domain.GeminiChatResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface GeminiApiClient {
    
    @RequestLine("POST /v1beta/models/{model}:generateContent")
    @Headers("Content-Type: application/json")
    GeminiChatResponse generateContent(@Param("model") String model, GeminiChatRequest request);
    
    @RequestLine("POST /v1beta/models/{model}:streamGenerateContent")
    @Headers("Content-Type: application/json")
    GeminiChatResponse streamGenerateContent(@Param("model") String model, GeminiChatRequest request);
    
    @RequestLine("POST /v1beta/models/{model}:countTokens")
    @Headers("Content-Type: application/json")
    TokenCountResponse countTokens(@Param("model") String model, GeminiChatRequest request);
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenCountResponse {
        @JsonProperty("totalTokens")
        private Integer totalTokens;
    }
}