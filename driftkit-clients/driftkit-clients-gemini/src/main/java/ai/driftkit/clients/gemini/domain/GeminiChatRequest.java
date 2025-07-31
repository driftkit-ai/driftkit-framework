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
public class GeminiChatRequest {
    
    @JsonProperty("contents")
    private List<GeminiContent> contents;
    
    @JsonProperty("systemInstruction")
    private GeminiContent systemInstruction;
    
    @JsonProperty("tools")
    private List<GeminiTool> tools;
    
    @JsonProperty("toolConfig")
    private ToolConfig toolConfig;
    
    @JsonProperty("generationConfig")
    private GeminiGenerationConfig generationConfig;
    
    @JsonProperty("safetySettings")
    private List<GeminiSafetySettings> safetySettings;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolConfig {
        @JsonProperty("functionCallingConfig")
        private FunctionCallingConfig functionCallingConfig;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionCallingConfig {
            @JsonProperty("mode")
            private String mode; // AUTO, ANY, NONE
            
            @JsonProperty("allowedFunctionNames")
            private List<String> allowedFunctionNames;
        }
    }
}