package ai.driftkit.vector.springai.autoconfigure;

import ai.driftkit.vector.core.domain.TextVectorStore;
import ai.driftkit.vector.springai.SpringAiVectorStoreAdapter;
import ai.driftkit.vector.springai.SpringAiVectorStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Spring AI vector store integration with DriftKit.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({org.springframework.ai.vectorstore.VectorStore.class, TextVectorStore.class})
@ConditionalOnProperty(prefix = "driftkit.vector.spring-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SpringAiVectorStoreProperties.class)
public class SpringAiVectorStoreAutoConfiguration {
    
    @Bean
    @ConditionalOnBean(org.springframework.ai.vectorstore.VectorStore.class)
    @ConditionalOnMissingBean(SpringAiVectorStoreAdapter.class)
    public TextVectorStore springAiVectorStoreAdapter(org.springframework.ai.vectorstore.VectorStore springAiVectorStore,
                                                       SpringAiVectorStoreProperties properties) {
        if (StringUtils.isBlank(properties.getStoreName())) {
            throw new IllegalStateException("Spring AI Vector Store name cannot be blank");
        }
        
        log.info("Creating Spring AI VectorStore adapter with name: {}", properties.getStoreName());
        return SpringAiVectorStoreFactory.create(springAiVectorStore, properties.getStoreName());
    }
}