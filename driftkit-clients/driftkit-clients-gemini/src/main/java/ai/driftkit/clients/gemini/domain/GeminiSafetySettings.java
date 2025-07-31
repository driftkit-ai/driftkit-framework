package ai.driftkit.clients.gemini.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiSafetySettings {
    
    @JsonProperty("category")
    private HarmCategory category;
    
    @JsonProperty("threshold")
    private HarmBlockThreshold threshold;
    
    public enum HarmCategory {
        HARM_CATEGORY_UNSPECIFIED("HARM_CATEGORY_UNSPECIFIED"),
        HARM_CATEGORY_DEROGATORY("HARM_CATEGORY_DEROGATORY"),
        HARM_CATEGORY_TOXICITY("HARM_CATEGORY_TOXICITY"),
        HARM_CATEGORY_VIOLENCE("HARM_CATEGORY_VIOLENCE"),
        HARM_CATEGORY_SEXUAL("HARM_CATEGORY_SEXUAL"),
        HARM_CATEGORY_MEDICAL("HARM_CATEGORY_MEDICAL"),
        HARM_CATEGORY_DANGEROUS("HARM_CATEGORY_DANGEROUS"),
        HARM_CATEGORY_HARASSMENT("HARM_CATEGORY_HARASSMENT"),
        HARM_CATEGORY_HATE_SPEECH("HARM_CATEGORY_HATE_SPEECH"),
        HARM_CATEGORY_SEXUALLY_EXPLICIT("HARM_CATEGORY_SEXUALLY_EXPLICIT"),
        HARM_CATEGORY_DANGEROUS_CONTENT("HARM_CATEGORY_DANGEROUS_CONTENT");
        
        private final String value;
        
        HarmCategory(String value) {
            this.value = value;
        }
        
        @JsonValue
        public String getValue() {
            return value;
        }
    }
    
    public enum HarmBlockThreshold {
        HARM_BLOCK_THRESHOLD_UNSPECIFIED("HARM_BLOCK_THRESHOLD_UNSPECIFIED"),
        BLOCK_LOW_AND_ABOVE("BLOCK_LOW_AND_ABOVE"),
        BLOCK_MEDIUM_AND_ABOVE("BLOCK_MEDIUM_AND_ABOVE"),
        BLOCK_ONLY_HIGH("BLOCK_ONLY_HIGH"),
        BLOCK_NONE("BLOCK_NONE");
        
        private final String value;
        
        HarmBlockThreshold(String value) {
            this.value = value;
        }
        
        @JsonValue
        public String getValue() {
            return value;
        }
    }
}