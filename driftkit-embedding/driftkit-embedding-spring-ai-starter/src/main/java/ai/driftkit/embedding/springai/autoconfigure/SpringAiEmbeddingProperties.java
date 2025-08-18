package ai.driftkit.embedding.springai.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Spring AI embedding integration.
 */
@Data
@ConfigurationProperties(prefix = "driftkit.embedding.spring-ai")
public class SpringAiEmbeddingProperties {
    
    /**
     * Whether Spring AI embedding integration is enabled.
     */
    private boolean enabled = true;
    
    /**
     * The name to use for the Spring AI embedding model adapter.
     * This name will be used when creating the adapter and for model lookup.
     */
    private String modelName = "spring-ai-default";
    
    /**
     * Whether to automatically create a default DriftKit EmbeddingModel bean
     * when a Spring AI EmbeddingModel is detected.
     */
    private boolean autoCreateAdapter = true;
}