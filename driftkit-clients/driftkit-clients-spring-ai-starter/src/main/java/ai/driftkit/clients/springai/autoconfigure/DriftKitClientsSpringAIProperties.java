package ai.driftkit.clients.springai.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for DriftKit Clients Spring AI integration.
 */
@Data
@ConfigurationProperties(prefix = "driftkit.clients.spring-ai")
public class DriftKitClientsSpringAIProperties {
    
    /**
     * Enable Spring AI model client bean creation
     */
    private boolean enabled = true;
    
    /**
     * Default model name to use if not specified in requests
     */
    private String defaultModel;
    
    /**
     * Default temperature for model requests
     */
    private Double defaultTemperature;
    
    /**
     * Default max tokens for model requests
     */
    private Integer defaultMaxTokens;
    
    /**
     * Default top-p value for model requests
     */
    private Double defaultTopP;
    
    /**
     * Enable request/response logging
     */
    private boolean loggingEnabled = false;
    
    /**
     * Model-specific configuration
     */
    private ModelProperties model = new ModelProperties();
    
    @Data
    public static class ModelProperties {
        /**
         * Bean name of the Spring AI ChatModel to use
         */
        private String beanName;
        
        /**
         * Whether to register as primary ModelClient
         */
        private boolean primary = false;
    }
}