package ai.driftkit.clients.gemini.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiGenerationConfig {
    
    @JsonProperty("temperature")
    private Double temperature;
    
    @JsonProperty("topP")
    private Double topP;
    
    @JsonProperty("topK")
    private Integer topK;
    
    @JsonProperty("candidateCount")
    private Integer candidateCount;
    
    @JsonProperty("maxOutputTokens")
    private Integer maxOutputTokens;
    
    @JsonProperty("stopSequences")
    private List<String> stopSequences;
    
    @JsonProperty("presencePenalty")
    private Double presencePenalty;
    
    @JsonProperty("frequencyPenalty")
    private Double frequencyPenalty;
    
    @JsonProperty("responseMimeType")
    private String responseMimeType; // text/plain, application/json, text/x.enum
    
    @JsonProperty("responseJsonSchema")
    private GeminiSchema responseJsonSchema; // JSON schema for structured output
    
    @JsonProperty("responseLogprobs")
    private Boolean responseLogprobs;
    
    @JsonProperty("logprobs")
    private Integer logprobs; // Number of top logprobs to return
    
    @JsonProperty("thinkingConfig")
    private ThinkingConfig thinkingConfig;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ThinkingConfig {
        @JsonProperty("thinkingBudget")
        private Integer thinkingBudget; // 0 to disable, -1 for dynamic, or specific token count
        
        @JsonProperty("includeThoughts")
        private Boolean includeThoughts; // Include synthesized thoughts in response
    }
}