package ai.driftkit.context.core.util;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.Prompt;
import ai.driftkit.context.core.registry.PromptServiceRegistry;
import ai.driftkit.context.core.service.PromptService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to load default prompts from resources and register them with PromptService.
 */
@Slf4j
public class DefaultPromptLoader {
    
    private static final String PROMPTS_RESOURCE_PATH = "/prompts/";
    private static final Map<String, String> PROMPT_CACHE = new ConcurrentHashMap<>();
    
    /**
     * Load a prompt from resources or PromptService.
     * 
     * @param promptId The ID of the prompt (e.g., "loop.agent.structured.evaluation")
     * @param variables Variables to apply to the prompt template
     * @return The processed prompt text, or null if not found
     */
    public static String loadPrompt(String promptId, Map<String, Object> variables) {
        // Try to get from PromptService first
        PromptService promptService = PromptServiceRegistry.getInstance();
        if (promptService != null) {
            // Load default from resources if not cached
            String defaultTemplate = loadDefaultTemplate(promptId);
            if (defaultTemplate != null) {
                Prompt prompt = promptService.createIfNotExists(
                    promptId,
                    defaultTemplate,
                    null,
                    false,
                    Language.GENERAL
                );
                
                if (prompt != null && prompt.getMessage() != null) {
                    return PromptUtils.applyVariables(prompt.getMessage(), variables);
                }
            }
        }
        
        // Fallback to loading directly from resources
        String template = loadDefaultTemplate(promptId);
        if (template != null) {
            return PromptUtils.applyVariables(template, variables);
        }
        
        return null;
    }
    
    /**
     * Load default template from resources.
     */
    private static String loadDefaultTemplate(String promptId) {
        // Check cache first
        String cached = PROMPT_CACHE.get(promptId);
        if (cached != null) {
            return cached;
        }
        
        // Load from resources
        String resourcePath = PROMPTS_RESOURCE_PATH + promptId + ".prompt";
        try (InputStream is = DefaultPromptLoader.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                String template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                PROMPT_CACHE.put(promptId, template);
                return template;
            }
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", resourcePath, e);
        }
        
        return null;
    }
}