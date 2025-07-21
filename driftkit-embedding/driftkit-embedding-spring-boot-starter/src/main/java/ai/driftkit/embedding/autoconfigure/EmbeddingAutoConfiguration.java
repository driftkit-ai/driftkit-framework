package ai.driftkit.embedding.autoconfigure;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.service.EmbeddingFactory;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for embedding services.
 * 
 * This configuration automatically creates an EmbeddingModel bean
 * from the EtlConfig.embedding configuration when available.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnBean(EtlConfig.class)
@ConditionalOnProperty(name = "driftkit.embedding.name")
public class EmbeddingAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel embeddingModel(EtlConfig config) {
        try {
            EmbeddingServiceConfig embeddingConfig = config.getEmbedding();
            
            if (embeddingConfig == null) {
                log.warn("No embedding configuration found in EtlConfig");
                return null;
            }
            
            log.info("Initializing embedding service: {}", embeddingConfig.getName());
            
            EmbeddingModel embeddingModel = EmbeddingFactory.fromName(
                embeddingConfig.getName(), 
                embeddingConfig.getConfig()
            );
            
            log.info("Successfully initialized embedding service: {}", embeddingConfig.getName());
            return embeddingModel;
            
        } catch (Exception e) {
            log.error("Failed to initialize embedding service from configuration", e);
            throw new RuntimeException("Failed to initialize embedding service", e);
        }
    }
}