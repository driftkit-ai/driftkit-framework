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
public class GeminiImageRequest {
    
    @JsonProperty("contents")
    private List<GeminiContent> contents;
    
    @JsonProperty("generationConfig")
    private ImageGenerationConfig generationConfig;
    
    @JsonProperty("safetySettings")
    private List<GeminiSafetySettings> safetySettings;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageGenerationConfig {
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
        
        @JsonProperty("responseModalities")
        private List<String> responseModalities; // ["TEXT", "IMAGE"]
    }
}