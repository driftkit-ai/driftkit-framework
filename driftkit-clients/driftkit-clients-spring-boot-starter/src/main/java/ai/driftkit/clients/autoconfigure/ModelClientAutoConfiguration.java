package ai.driftkit.clients.autoconfigure;

import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VaultConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for model client services.
 * 
 * This configuration automatically creates ModelClient beans
 * from the EtlConfig.vault configurations when available.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnBean(EtlConfig.class)
@ConditionalOnProperty(name = "driftkit.vault[0].name")
public class ModelClientAutoConfiguration {
    
    @Bean("primaryModelClient")
    @ConditionalOnMissingBean(name = "primaryModelClient")
    public ModelClient primaryModelClient(EtlConfig config) {
        try {
            List<VaultConfig> vaultConfigs = config.getVault();
            
            if (vaultConfigs == null || vaultConfigs.isEmpty()) {
                log.warn("No vault configurations found in EtlConfig");
                return null;
            }
            
            // Use the first vault config as primary
            VaultConfig primaryConfig = vaultConfigs.get(0);
            log.info("Initializing primary model client: {}", primaryConfig.getName());
            
            ModelClient modelClient = ModelClientFactory.fromConfig(primaryConfig);
            
            log.info("Successfully initialized primary model client: {}", primaryConfig.getName());
            return modelClient;
            
        } catch (Exception e) {
            log.error("Failed to initialize primary model client from configuration", e);
            throw new RuntimeException("Failed to initialize primary model client", e);
        }
    }
    
    @Bean
    @ConditionalOnMissingBean(ModelClient.class)
    public ModelClient modelClient(EtlConfig config) {
        // Fallback to primary model client
        return primaryModelClient(config);
    }
}