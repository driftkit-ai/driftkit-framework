package ai.driftkit.context.springai;

import ai.driftkit.common.domain.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Map;

/**
 * Enhanced ChatClient that integrates DriftKit prompt management with Spring AI.
 * This wrapper provides convenient methods to use DriftKit prompts directly
 * with Spring AI ChatClient.
 * 
 * Usage example:
 * <pre>
 * @Component
 * public class MyService {
 *     private final DriftKitChatClient chatClient;
 *     
 *     public String analyzeSentiment(String review) {
 *         // Use DriftKit prompt with variables
 *         return chatClient.promptById("sentiment.analysis")
 *             .withVariable("review", review)
 *             .withLanguage(Language.ENGLISH)
 *             .call()
 *             .content();
 *     }
 *     
 *     public ProductInfo extractProductInfo(String description) {
 *         // Use DriftKit prompt and map to entity
 *         return chatClient.promptById("product.extraction")
 *             .withVariable("description", description)
 *             .call()
 *             .entity(ProductInfo.class);
 *     }
 * }
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class DriftKitChatClient {
    
    private final ChatClient chatClient;
    private final DriftKitPromptProvider promptProvider;
    
    /**
     * Start building a prompt from DriftKit prompt ID
     * 
     * @param promptId The DriftKit prompt ID
     * @return DriftKitPromptSpec for fluent configuration
     */
    public DriftKitPromptSpec promptById(String promptId) {
        return new DriftKitPromptSpec(promptId);
    }
    
    /**
     * Get the underlying Spring AI ChatClient for direct access
     */
    public ChatClient getChatClient() {
        return chatClient;
    }
    
    /**
     * Get the prompt provider for direct access
     */
    public DriftKitPromptProvider getPromptProvider() {
        return promptProvider;
    }
    
    /**
     * Fluent builder for DriftKit prompt execution
     */
    public class DriftKitPromptSpec {
        private final String promptId;
        private final Map<String, Object> variables = new java.util.HashMap<>();
        private Language language = Language.GENERAL;
        
        private DriftKitPromptSpec(String promptId) {
            this.promptId = promptId;
        }
        
        /**
         * Add a variable to the prompt
         */
        public DriftKitPromptSpec withVariable(String name, Object value) {
            this.variables.put(name, value);
            return this;
        }
        
        /**
         * Add multiple variables to the prompt
         */
        public DriftKitPromptSpec withVariables(Map<String, Object> vars) {
            this.variables.putAll(vars);
            return this;
        }
        
        /**
         * Set the language for the prompt
         */
        public DriftKitPromptSpec withLanguage(Language language) {
            this.language = language;
            return this;
        }
        
        /**
         * Execute the prompt and return response spec
         */
        public CallResponseSpec call() {
            // Get prompt configuration from DriftKit
            DriftKitPromptProvider.PromptConfiguration config = 
                promptProvider.getPromptWithVariables(promptId, variables, language);
            
            // Build ChatClient prompt
            var promptSpec = chatClient.prompt();
            
            // Add system message if present
            if (config.hasSystemMessage()) {
                promptSpec.system(config.getSystemMessage());
            }
            
            // Add user message
            promptSpec.user(config.getUserMessage());
            
            // Apply temperature if specified
            if (config.getTemperature() != null) {
                promptSpec.options(ChatOptions.builder()
                                .temperature(config.getTemperature())
                                .build()
                );
            }
            
            return promptSpec.call();
        }
        
        /**
         * Execute the prompt and return content directly
         */
        public String content() {
            return call().content();
        }
        
        /**
         * Execute the prompt and map to entity
         */
        public <T> T entity(Class<T> type) {
            return call().entity(type);
        }
        
        /**
         * Execute the prompt and return full ChatResponse
         */
        public ChatResponse chatResponse() {
            return call().chatResponse();
        }
        
        /**
         * Stream the response
         */
        public reactor.core.publisher.Flux<String> stream() {
            // Get prompt configuration from DriftKit
            DriftKitPromptProvider.PromptConfiguration config = 
                promptProvider.getPromptWithVariables(promptId, variables, language);
            
            // Build ChatClient prompt
            var promptSpec = chatClient.prompt();
            
            // Add system message if present
            if (config.hasSystemMessage()) {
                promptSpec.system(config.getSystemMessage());
            }
            
            // Add user message
            promptSpec.user(config.getUserMessage());
            
            // Apply temperature if specified
            if (config.getTemperature() != null) {
                promptSpec.options(ChatOptions.builder()
                        .temperature(config.getTemperature())
                        .build()
                );
            }
            
            // Stream
            return promptSpec.stream().content();
        }
    }
}