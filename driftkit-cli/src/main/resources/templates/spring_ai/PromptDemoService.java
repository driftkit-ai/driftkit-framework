package /*PACKAGE_NAME*/.service;

import ai.driftkit.context.engineering.domain.Language;
import ai.driftkit.spring.ai.DriftKitChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptDemoService {
    
    private final DriftKitChatClient driftKitChatClient;
    private final ChatClient chatClient;
    
    public Map<String, String> demonstrateMultilingualPrompts() {
        Map<String, String> results = new HashMap<>();
        
        // Same prompt in different languages
        for (Language language : Language.values()) {
            try {
                String response = driftKitChatClient
                    .promptById("greeting.welcome")
                    .withVariable("userName", "Demo User")
                    .withLanguage(language)
                    .call()
                    .content();
                
                results.put(language.name(), response);
            } catch (Exception e) {
                results.put(language.name(), "Not configured for " + language);
            }
        }
        
        return results;
    }
    
    public Map<String, Object> demonstrateTemplateVariables() {
        Map<String, Object> variables = Map.of(
            "productName", "DriftKit AI Framework",
            "features", new String[]{"Prompt Management", "Spring AI Integration", "Multi-language Support"},
            "version", "0.6.0"
        );
        
        String result = driftKitChatClient
            .promptById("product.description")
            .withVariables(variables)
            .call()
            .content();
        
        return Map.of(
            "variables", variables,
            "generatedDescription", result
        );
    }
    
    public Map<String, Object> demonstrateStructuredOutput() {
        // Generate structured product analysis
        ProductAnalysis analysis = chatClient.prompt()
            .user("Analyze the DriftKit AI Framework and provide a structured analysis including strengths, weaknesses, and market position.")
            .call()
            .entity(ProductAnalysis.class);
        
        return Map.of(
            "structuredAnalysis", analysis,
            "type", "ProductAnalysis"
        );
    }
    
    public Map<String, Object> demonstrateDriftKitFeatures() {
        Map<String, Object> features = new HashMap<>();
        
        // 1. Prompt with tracing
        String tracedResponse = driftKitChatClient
            .promptById("demo.tracing")
            .withVariable("feature", "Request Tracing")
            .enableTracing(true)
            .call()
            .content();
        features.put("tracingDemo", tracedResponse);
        
        // 2. Prompt with custom temperature
        String customTempResponse = driftKitChatClient
            .promptById("demo.creative")
            .withVariable("topic", "AI Applications")
            .withTemperature(0.9)
            .call()
            .content();
        features.put("customTemperature", customTempResponse);
        
        // 3. Fallback demonstration
        try {
            String fallbackResponse = driftKitChatClient
                .promptById("non.existent.prompt")
                .withFallback("This is a fallback response when prompt is not found")
                .call()
                .content();
            features.put("fallbackHandling", fallbackResponse);
        } catch (Exception e) {
            features.put("fallbackHandling", "Error: " + e.getMessage());
        }
        
        return features;
    }
    
    // Structured output class
    @lombok.Data
    public static class ProductAnalysis {
        private String productName;
        private String[] strengths;
        private String[] weaknesses;
        private String marketPosition;
        private double overallScore;
        private Map<String, String> recommendations;
    }
}