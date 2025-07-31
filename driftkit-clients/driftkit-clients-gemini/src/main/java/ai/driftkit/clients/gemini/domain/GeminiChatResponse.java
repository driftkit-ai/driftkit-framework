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
public class GeminiChatResponse {
    
    @JsonProperty("candidates")
    private List<Candidate> candidates;
    
    @JsonProperty("usageMetadata")
    private UsageMetadata usageMetadata;
    
    @JsonProperty("modelVersion")
    private String modelVersion;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Candidate {
        @JsonProperty("content")
        private GeminiContent content;
        
        @JsonProperty("finishReason")
        private String finishReason; // STOP, SAFETY, MAX_TOKENS, etc.
        
        @JsonProperty("safetyRatings")
        private List<SafetyRating> safetyRatings;
        
        @JsonProperty("citationMetadata")
        private CitationMetadata citationMetadata;
        
        @JsonProperty("tokenCount")
        private Integer tokenCount;
        
        @JsonProperty("groundingAttributions")
        private List<GroundingAttribution> groundingAttributions;
        
        @JsonProperty("logprobsResult")
        private LogprobsResult logprobsResult;
        
        @JsonProperty("index")
        private Integer index;
        
        @JsonProperty("thoughts")
        private List<String> thoughts; // Synthesized thoughts when includeThoughts is true
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageMetadata {
        @JsonProperty("promptTokenCount")
        private Integer promptTokenCount;
        
        @JsonProperty("candidatesTokenCount")
        private Integer candidatesTokenCount;
        
        @JsonProperty("totalTokenCount")
        private Integer totalTokenCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SafetyRating {
        @JsonProperty("category")
        private String category;
        
        @JsonProperty("probability")
        private String probability; // NEGLIGIBLE, LOW, MEDIUM, HIGH
        
        @JsonProperty("blocked")
        private Boolean blocked;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitationMetadata {
        @JsonProperty("citations")
        private List<Citation> citations;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        @JsonProperty("startIndex")
        private Integer startIndex;
        
        @JsonProperty("endIndex")
        private Integer endIndex;
        
        @JsonProperty("uri")
        private String uri;
        
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("license")
        private String license;
        
        @JsonProperty("publicationDate")
        private String publicationDate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroundingAttribution {
        @JsonProperty("sourceId")
        private String sourceId;
        
        @JsonProperty("content")
        private GeminiContent content;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogprobsResult {
        @JsonProperty("topCandidates")
        private List<TopCandidate> topCandidates;
        
        @JsonProperty("chosenCandidates")
        private List<TopCandidate> chosenCandidates;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopCandidate {
        @JsonProperty("token")
        private String token;
        
        @JsonProperty("tokenId")
        private Integer tokenId;
        
        @JsonProperty("logProbability")
        private Double logProbability;
    }
}