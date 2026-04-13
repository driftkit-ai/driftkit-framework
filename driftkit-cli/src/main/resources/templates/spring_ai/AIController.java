package /*PACKAGE_NAME*/.controller;

import /*PACKAGE_NAME*/.service.AIService;
import /*PACKAGE_NAME*/.service.PromptDemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {
    
    private final AIService aiService;
    private final PromptDemoService promptDemoService;
    
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Processing chat request: {}", request.getMessage());
        
        String response = aiService.chat(
            request.getMessage(),
            request.getSessionId(),
            request.getOptions()
        );
        
        return ResponseEntity.ok(new ChatResponse(response));
    }
    
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(@RequestBody GenerateRequest request) {
        log.info("Processing generation request with prompt: {}", request.getPromptId());
        
        String result = aiService.generateWithPrompt(
            request.getPromptId(),
            request.getVariables(),
            request.getLanguage()
        );
        
        return ResponseEntity.ok(new GenerateResponse(result));
    }
    
    @PostMapping("/structured")
    public ResponseEntity<StructuredResponse> generateStructured(@RequestBody StructuredRequest request) {
        log.info("Processing structured generation request");
        
        var result = aiService.generateStructuredOutput(
            request.getPromptId(),
            request.getVariables(),
            request.getOutputClass()
        );
        
        return ResponseEntity.ok(new StructuredResponse(result));
    }
    
    @PostMapping("/rag")
    public ResponseEntity<RAGResponse> ragQuery(@RequestBody RAGRequest request) {
        log.info("Processing RAG query: {}", request.getQuery());
        
        var result = aiService.ragQuery(
            request.getQuery(),
            request.getMaxResults(),
            request.isIncludeSources()
        );
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/demo")
    public ResponseEntity<Map<String, Object>> runDemos() {
        log.info("Running AI demos");
        
        Map<String, Object> results = Map.of(
            "multilingual", promptDemoService.demonstrateMultilingualPrompts(),
            "templateVariables", promptDemoService.demonstrateTemplateVariables(),
            "structuredOutput", promptDemoService.demonstrateStructuredOutput(),
            "driftKitIntegration", promptDemoService.demonstrateDriftKitFeatures()
        );
        
        return ResponseEntity.ok(results);
    }
    
    // Request/Response DTOs
    @lombok.Data
    public static class ChatRequest {
        private String message;
        private String sessionId;
        private Map<String, Object> options;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ChatResponse {
        private String response;
    }
    
    @lombok.Data
    public static class GenerateRequest {
        private String promptId;
        private Map<String, Object> variables;
        private String language = "ENGLISH";
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GenerateResponse {
        private String result;
    }
    
    @lombok.Data
    public static class StructuredRequest {
        private String promptId;
        private Map<String, Object> variables;
        private String outputClass;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StructuredResponse {
        private Object result;
    }
    
    @lombok.Data
    public static class RAGRequest {
        private String query;
        private int maxResults = 5;
        private boolean includeSources = true;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RAGResponse {
        private String answer;
        private List<Source> sources;
        private double confidence;
        
        @lombok.Data
        @lombok.AllArgsConstructor
        public static class Source {
            private String content;
            private double relevanceScore;
            private Map<String, Object> metadata;
        }
    }
}