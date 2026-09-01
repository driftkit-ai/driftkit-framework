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
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Auto-configuration for model client services.
 * <p>
 * Creates a single {@code primaryModelClient} bean from the first {@code driftkit.vault[]} entry.
 * Skipped entirely when the application already defines a {@link ModelClient} bean (for example the
 * Spring AI adapter from {@code driftkit-clients-spring-ai-starter}).
 */
@Slf4j
@AutoConfiguration(after = EtlConfigAutoConfiguration.class)
@ConditionalOnBean(EtlConfig.class)
@ConditionalOnProperty(name = "driftkit.vault[0].name")
public class ModelClientAutoConfiguration {

    @Bean("primaryModelClient")
    @Primary
    @ConditionalOnMissingBean(ModelClient.class)
    public ModelClient<?> primaryModelClient(EtlConfig config) {
        List<VaultConfig> vaultConfigs = config.getVault();
        if (vaultConfigs == null || vaultConfigs.isEmpty()) {
            throw new IllegalStateException("driftkit.vault is empty although driftkit.vault[0].name is set");
        }

        // Use the first vault config as primary
        VaultConfig primaryConfig = vaultConfigs.get(0);
        log.info("Initializing primary model client from vault entry '{}'", primaryConfig.getName());

        ModelClient<?> modelClient = ModelClientFactory.fromConfig(primaryConfig);

        log.info("Successfully initialized primary model client: {}", modelClient.getClass().getSimpleName());
        return modelClient;
    }
}
