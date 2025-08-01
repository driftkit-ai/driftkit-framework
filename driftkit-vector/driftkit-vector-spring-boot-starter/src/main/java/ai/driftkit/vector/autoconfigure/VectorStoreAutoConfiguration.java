package ai.driftkit.vector.autoconfigure;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import ai.driftkit.vector.core.domain.BaseVectorStore;
import ai.driftkit.vector.core.service.VectorStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Auto-configuration for vector store services.
 * 
 * This configuration automatically creates a BaseVectorStore bean
 * from the EtlConfig.vectorStore configuration when available.
 */
@Slf4j
@AutoConfiguration(after = ai.driftkit.config.autoconfigure.EtlConfigAutoConfiguration.class)
@ComponentScan(basePackages = {
    "ai.driftkit.vector.spring.service",
    "ai.driftkit.vector.spring.config",
    "ai.driftkit.vector.spring.controller",
    "ai.driftkit.vector.spring.parser"
})
@EnableMongoRepositories(basePackages = "ai.driftkit.vector.spring.repository")
public class VectorStoreAutoConfiguration {
    
    public VectorStoreAutoConfiguration() {
        log.info("Initializing DriftKit Vector Store Auto-Configuration");
    }
    
    @Bean
    @ConditionalOnMissingBean(BaseVectorStore.class)
    public BaseVectorStore vectorStore(EtlConfig config) {
        try {
            VectorStoreConfig vectorStoreConfig = config.getVectorStore();
            
            if (vectorStoreConfig == null) {
                log.warn("No vector store configuration found in EtlConfig");
                return null;
            }
            
            log.info("Initializing vector store: {}", vectorStoreConfig.getName());
            
            BaseVectorStore vectorStore = VectorStoreFactory.fromConfig(vectorStoreConfig);
            
            log.info("Successfully initialized vector store: {}", vectorStoreConfig.getName());
            return vectorStore;
            
        } catch (Exception e) {
            log.error("Failed to initialize vector store from configuration", e);
            throw new RuntimeException("Failed to initialize vector store", e);
        }
    }
}