package ai.driftkit.context.springai;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.context.core.util.PromptUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider for DriftKit prompts to be used with Spring AI ChatClient.
 * This allows seamless integration of DriftKit's prompt management 
 * with Spring AI's fluent API.
 * 
 * Usage example:
 * <pre>
 * @Component
 * public class MyService {
 *     private final ChatClient chatClient;
 *     private final DriftKitPromptProvider promptProvider;
 *     
 *     public String classifySentiment(String review) {
 *         // Get prompt configuration from DriftKit
 *         var promptConfig = promptProvider.getPrompt("sentiment.analysis", Language.ENGLISH);
 *         
 *         return chatClient.prompt()
 *             .system(promptConfig.getSystemMessage())
 *             .user(u -> u.text(promptConfig.getUserMessage())
 *                 .param("review", review))
 *             .options(opt -> opt.temperature(promptConfig.getTemperature()))
 *             .call()
 *             .entity(Sentiment.class);
 *     }
 * }
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class DriftKitPromptProvider {
    
    private final PromptService promptService;
    
    /**
     * Get prompt configuration for use with Spring AI ChatClient
     * 
     * @param promptId The DriftKit prompt ID
     * @return PromptConfiguration ready for Spring AI
     */
    public PromptConfiguration getPrompt(String promptId) {
        return getPrompt(promptId, Language.GENERAL);
    }
    
    /**
     * Get prompt configuration with language support
     * 
     * @param promptId The DriftKit prompt ID
     * @param language The desired language
     * @return PromptConfiguration ready for Spring AI
     */
    public PromptConfiguration getPrompt(String promptId, Language language) {
        // First try to get prompt for specific language
        List<Prompt> prompts = promptService.getPromptsByMethods(List.of(promptId));
        
        Optional<Prompt> promptOpt = prompts.stream()
            .filter(p -> p.getLanguage() == language)
            .findFirst();
        
        // Fall back to GENERAL language if specific language not found
        if (promptOpt.isEmpty() && language != Language.GENERAL) {
            promptOpt = prompts.stream()
                .filter(p -> p.getLanguage() == Language.GENERAL)
                .findFirst();
            
            if (promptOpt.isPresent()) {
                log.debug("Prompt {} not found for language {}, falling back to GENERAL", 
                    promptId, language);
            }
        }
        
        if (promptOpt.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("Prompt not found: %s for language: %s", promptId, language));
        }
        
        Prompt prompt = promptOpt.get();
        
        return PromptConfiguration.builder()
            .promptId(promptId)
            .userMessage(prompt.getMessage())
            .systemMessage(prompt.getSystemMessage())
            .temperature(prompt.getTemperature())
            .jsonResponse(prompt.isJsonResponse())
            .responseFormat(prompt.getResponseFormat())
            .language(prompt.getLanguage())
            .build();
    }
    
    /**
     * Get prompt with variables already applied
     * 
     * @param promptId The DriftKit prompt ID
     * @param variables Variables to apply to the prompt
     * @return PromptConfiguration with processed messages
     */
    public PromptConfiguration getPromptWithVariables(String promptId, Map<String, Object> variables) {
        return getPromptWithVariables(promptId, variables, Language.GENERAL);
    }
    
    /**
     * Get prompt with variables already applied and language support
     * 
     * @param promptId The DriftKit prompt ID
     * @param variables Variables to apply to the prompt
     * @param language The desired language
     * @return PromptConfiguration with processed messages
     */
    public PromptConfiguration getPromptWithVariables(String promptId, Map<String, Object> variables, Language language) {
        PromptConfiguration config = getPrompt(promptId, language);
        
        // Apply variables to messages
        String processedUserMessage = PromptUtils.applyVariables(config.getUserMessage(), variables);
        String processedSystemMessage = config.getSystemMessage() != null 
            ? PromptUtils.applyVariables(config.getSystemMessage(), variables)
            : null;
        
        return config.toBuilder()
            .userMessage(processedUserMessage)
            .systemMessage(processedSystemMessage)
            .build();
    }
    
    /**
     * Check if a prompt exists for a given ID and language
     * 
     * @param promptId The DriftKit prompt ID
     * @param language The language to check
     * @return true if prompt exists
     */
    public boolean hasPrompt(String promptId, Language language) {
        List<Prompt> prompts = promptService.getPromptsByMethods(List.of(promptId));
        return prompts.stream().anyMatch(p -> p.getLanguage() == language);
    }
    
    /**
     * Get all available languages for a prompt
     * 
     * @param promptId The DriftKit prompt ID
     * @return List of available languages
     */
    public List<Language> getAvailableLanguages(String promptId) {
        List<Prompt> prompts = promptService.getPromptsByMethods(List.of(promptId));
        return prompts.stream()
            .map(Prompt::getLanguage)
            .distinct()
            .toList();
    }
    
    /**
     * Configuration holder for DriftKit prompts
     */
    @lombok.Data
    @lombok.Builder(toBuilder = true)
    public static class PromptConfiguration {
        private final String promptId;
        private final String userMessage;
        private final String systemMessage;
        private final Double temperature;
        private final boolean jsonResponse;
        private final ai.driftkit.common.domain.client.ResponseFormat responseFormat;
        private final Language language;
        
        /**
         * Check if this prompt has a system message
         */
        public boolean hasSystemMessage() {
            return systemMessage != null && !systemMessage.isBlank();
        }
        
        /**
         * Get temperature or default value
         */
        public double getTemperatureOrDefault(double defaultValue) {
            return temperature != null ? temperature : defaultValue;
        }
    }
}