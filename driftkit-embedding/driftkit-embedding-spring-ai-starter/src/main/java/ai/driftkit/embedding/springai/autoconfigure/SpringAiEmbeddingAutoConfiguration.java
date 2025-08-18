package ai.driftkit.embedding.springai.autoconfigure;

import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.embedding.springai.SpringAiEmbeddingAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for Spring AI embedding model integration.
 * This configuration automatically creates DriftKit EmbeddingModel adapters
 * for any Spring AI EmbeddingModel beans found in the application context.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({org.springframework.ai.embedding.EmbeddingModel.class, EmbeddingModel.class})
@EnableConfigurationProperties(SpringAiEmbeddingProperties.class)
public class SpringAiEmbeddingAutoConfiguration {

    /**
     * Creates a DriftKit EmbeddingModel adapter for Spring AI EmbeddingModel bean.
     * The adapter allows using Spring AI embedding models through the DriftKit interface.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "driftkit.embedding.spring-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class SpringAiEmbeddingAdapterConfiguration {

        /**
         * Creates a default DriftKit EmbeddingModel bean if configured.
         * This allows @Autowired EmbeddingModel to work when only Spring AI is configured.
         */
        @Bean
        @ConditionalOnBean(org.springframework.ai.embedding.EmbeddingModel.class)
        @ConditionalOnMissingBean(EmbeddingModel.class)
        @ConditionalOnProperty(prefix = "driftkit.embedding.spring-ai", name = "auto-create-adapter", havingValue = "true", matchIfMissing = true)
        public EmbeddingModel springAiEmbeddingAdapter(
                org.springframework.ai.embedding.EmbeddingModel springAiModel,
                SpringAiEmbeddingProperties properties) {
            
            String modelName = properties.getModelName();
            if (modelName == null) {
                modelName = "spring-ai-default";
            }
            
            log.info("Creating Spring AI embedding adapter with name: {}", modelName);
            return new SpringAiEmbeddingAdapter(springAiModel, modelName);
        }
    }
}