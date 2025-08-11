package ai.driftkit.rag.spring.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration properties for DriftKit RAG.
 */
@Data
@ConfigurationProperties(prefix = "driftkit.rag")
public class RagProperties {
    
    /**
     * Default index name for vector store operations.
     */
    private String defaultIndex = "default";
    
    /**
     * Enable RAG features.
     */
    private boolean enabled = true;
    
    /**
     * Text splitter configuration.
     */
    private SplitterProperties splitter = new SplitterProperties();
    
    /**
     * Reranker configuration.
     */
    private RerankerProperties reranker = new RerankerProperties();
    
    /**
     * Retriever configuration.
     */
    private RetrieverProperties retriever = new RetrieverProperties();
    
    /**
     * Ingestion pipeline configuration.
     */
    private IngestionProperties ingestion = new IngestionProperties();
    
    @Data
    public static class SplitterProperties {
        /**
         * Type of splitter: recursive or semantic.
         */
        private String type = "recursive";
        
        /**
         * Chunk size for recursive splitter.
         */
        private int chunkSize = 512;
        
        /**
         * Overlap between chunks for recursive splitter.
         */
        private int chunkOverlap = 128;
        
        /**
         * Target chunk size for semantic splitter.
         */
        private int targetChunkSize = 512;
        
        /**
         * Similarity threshold for semantic splitter.
         */
        private float similarityThreshold = 0.7f;
        
        /**
         * Maximum chunk size.
         */
        private int maxChunkSize = 1024;
        
        /**
         * Minimum chunk size.
         */
        private int minChunkSize = 100;
        
        /**
         * Whether to preserve document metadata in chunks.
         */
        private boolean preserveMetadata = true;
        
        /**
         * Whether to add chunk-specific metadata.
         */
        private boolean addChunkMetadata = true;
    }
    
    @Data
    public static class RerankerProperties {
        /**
         * Enable reranking.
         */
        private boolean enabled = true;
        
        /**
         * Model to use for reranking.
         */
        private String model = "gpt-4o";
        
        /**
         * Temperature for reranking model.
         */
        private float temperature = 0.0f;
        
        /**
         * Prompt ID for reranking.
         */
        private String promptId = "rag.rerank";
    }
    
    @Data
    public static class RetrieverProperties {
        /**
         * Default number of results to retrieve.
         */
        private int defaultTopK = 10;
        
        /**
         * Default minimum score threshold.
         */
        private float defaultMinScore = 0.0f;
        
        /**
         * Query prefix to add to all queries.
         */
        private String queryPrefix = "";
    }
    
    @Data
    public static class IngestionProperties {
        /**
         * Maximum retries for failed documents.
         */
        private int maxRetries = 3;
        
        /**
         * Retry delay in milliseconds.
         */
        private long retryDelayMs = 1000;
        
        /**
         * Use virtual threads for processing.
         */
        private boolean useVirtualThreads = true;
        
        /**
         * Default file extensions to process.
         */
        private Set<String> defaultExtensions = Set.of(
            "txt", "md", "json", "xml", "html", "csv",
            "pdf", "doc", "docx", "odt",
            "jpg", "jpeg", "png"
        );
        
        /**
         * Maximum file size in bytes.
         */
        private long maxFileSizeBytes = 50 * 1024 * 1024; // 50MB
    }
}