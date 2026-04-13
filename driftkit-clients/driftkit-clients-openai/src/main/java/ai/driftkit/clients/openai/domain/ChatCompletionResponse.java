package ai.driftkit.clients.openai.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionResponse {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private OpenAIUsage usage;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAIUsage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
        @JsonProperty("prompt_tokens_details")
        private PromptTokensDetails promptTokensDetails;
        @JsonProperty("completion_tokens_details")
        private CompletionTokensDetails completionTokensDetails;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptTokensDetails {
        @JsonProperty("cached_tokens")
        private Integer cachedTokens;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompletionTokensDetails {
        @JsonProperty("reasoning_tokens")
        private Integer reasoningTokens;
    }

    @Data
    public static class Choice {
        private Integer index;
        private Message message;
        @JsonProperty("finish_reason")
        private String finishReason;
        private ChatCompletionResponse.LogProbs logprobs;
    }
    
    @Data
    @Builder
    public static class LogProbs {
        private List<TokenLogprob> content;
        
        @Data
        @Builder
        public static class TokenLogprob {
            private String token;
            private Double logprob;
            @JsonProperty("top_logprobs")
            private List<TopLogprob> topLogprobs;
            private byte[] bytes;
            
            @Data
            @Builder
            public static class TopLogprob {
                private String token;
                private Double logprob;
                private byte[] bytes;
            }
        }
    }

    @Data
    public static class Message {
        private String role;
        private String content;
        @JsonProperty("reasoning_content")
        private String reasoningContent;
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }

    @Data
    public static class ToolCall {
        private String id;
        private String type;
        private Function function;
        
        @Data
        public static class Function {
            private String name;
            private String arguments;
        }
    }
}