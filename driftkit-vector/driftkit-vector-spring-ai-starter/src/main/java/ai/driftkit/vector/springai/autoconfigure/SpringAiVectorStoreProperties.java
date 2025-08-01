package ai.driftkit.vector.springai.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Spring AI vector store integration.
 */
@Data
@ConfigurationProperties(prefix = "driftkit.vector.spring-ai")
public class SpringAiVectorStoreProperties {
    
    /**
     * Whether to enable Spring AI vector store integration.
     * Default: true
     */
    private boolean enabled = true;
    
    /**
     * The name to use for the Spring AI vector store adapter.
     * Default: "spring-ai"
     */
    private String storeName = "spring-ai";
    
    /**
     * Whether to automatically register the adapter with VectorStoreFactory.
     * Default: true
     */
    private boolean autoRegister = true;
}