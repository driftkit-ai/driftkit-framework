package ai.driftkit.config.autoconfigure;

import ai.driftkit.config.EtlConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Base auto-configuration for EtlConfig.
 * This configuration MUST load first before any other DriftKit configurations.
 * 
 * EtlConfig is the foundation of the entire DriftKit framework and contains
 * all necessary configuration for various services.
 */
@Slf4j
@AutoConfiguration
public class EtlConfigAutoConfiguration {

    /**
     * Creates EtlConfig bean from application properties.
     * 
     * This bean can be configured in application.yml/properties:
     * <pre>
     * driftkit:
     *   promptService:
     *     name: mongodb
     *     config:
     *       collection: prompts
     *   vectorStore:
     *     name: mongodb
     *     config:
     *       collection: vectors
     *   embedding:
     *     name: openai
     *     config:
     *       apiKey: ${OPENAI_API_KEY}
     * </pre>
     * 
     * If no configuration is provided, creates a default EtlConfig with
     * file-based prompt service as a fallback.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "driftkit")
    public EtlConfig etlConfig() {
        log.info("Creating EtlConfig bean from configuration properties");
        EtlConfig config = new EtlConfig();
        
        // If no prompt service is configured, log a warning
        if (config.getPromptService() == null) {
            log.warn("========================================");
            log.warn("WARNING: No prompt service configuration found!");
            log.warn("DriftKit services that depend on prompt service may not work properly.");
            log.warn("Please configure driftkit.promptService in your application properties.");
            log.warn("========================================");
        }
        
        return config;
    }
}