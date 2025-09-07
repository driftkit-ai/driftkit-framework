package ai.driftkit.clients.openai.domain;

import ai.driftkit.common.domain.client.ModelTextResponse.Usage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a streaming chunk from OpenAI's chat completion API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionChunk {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("created")
    private Long created;
    
    @JsonProperty("model")
    private String model;
    
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;
    
    @JsonProperty("choices")
    private List<ChunkChoice> choices;
    
    @JsonProperty("usage")
    private Usage usage;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChunkChoice {
        @JsonProperty("index")
        private Integer index;
        
        @JsonProperty("delta")
        private Delta delta;
        
        @JsonProperty("logprobs")
        private ChatCompletionResponse.LogProbs logprobs;
        
        @JsonProperty("finish_reason")
        private String finishReason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        @JsonProperty("role")
        private String role;
        
        @JsonProperty("content")
        private String content;
        
        @JsonProperty("tool_calls")
        private List<DeltaToolCall> toolCalls;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeltaToolCall {
        @JsonProperty("index")
        private Integer index;
        
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("function")
        private DeltaFunction function;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeltaFunction {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("arguments")
        private String arguments;
    }
}