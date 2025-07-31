package ai.driftkit.clients.gemini.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiContent {
    
    @JsonProperty("role")
    private String role; // user, model, function
    
    @JsonProperty("parts")
    private List<Part> parts;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Part {
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("inlineData")
        private InlineData inlineData;
        
        @JsonProperty("fileData")
        private FileData fileData;
        
        @JsonProperty("functionCall")
        private FunctionCall functionCall;
        
        @JsonProperty("functionResponse")
        private FunctionResponse functionResponse;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class InlineData {
            @JsonProperty("mimeType")
            private String mimeType;
            
            @JsonProperty("data")
            private String data; // Base64 encoded
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FileData {
            @JsonProperty("mimeType")
            private String mimeType;
            
            @JsonProperty("fileUri")
            private String fileUri;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionCall {
            @JsonProperty("name")
            private String name;
            
            @JsonProperty("args")
            private Map<String, Object> args;
        }
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionResponse {
            @JsonProperty("name")
            private String name;
            
            @JsonProperty("response")
            private Map<String, Object> response;
        }
    }
}