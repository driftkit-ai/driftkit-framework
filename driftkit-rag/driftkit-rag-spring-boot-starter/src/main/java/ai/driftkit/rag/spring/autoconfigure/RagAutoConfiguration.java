package ai.driftkit.rag.spring.autoconfigure;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.rag.core.reranker.ModelBasedReranker;
import ai.driftkit.rag.core.retriever.VectorStoreRetriever;
import ai.driftkit.rag.core.splitter.RecursiveCharacterTextSplitter;
import ai.driftkit.rag.core.splitter.SemanticTextSplitter;
import ai.driftkit.rag.ingestion.IngestionPipeline;
import ai.driftkit.rag.retrieval.RetrievalPipeline;
import ai.driftkit.rag.spring.service.DocumentLoaderFactory;
import ai.driftkit.rag.spring.service.RagService;
import ai.driftkit.vector.core.domain.BaseVectorStore;
import ai.driftkit.vector.spring.parser.UnifiedParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot Auto Configuration for DriftKit RAG.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({IngestionPipeline.class, RetrievalPipeline.class})
@EnableConfigurationProperties(RagProperties.class)
public class RagAutoConfiguration {
    
    /**
     * Document loader factory.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(UnifiedParser.class)
    public DocumentLoaderFactory documentLoaderFactory(UnifiedParser parser) {
        log.info("Creating DocumentLoaderFactory");
        return new DocumentLoaderFactory(parser);
    }
    
    /**
     * Configuration for text splitters.
     */
    @Configuration
    @ConditionalOnClass(RecursiveCharacterTextSplitter.class)
    public static class TextSplitterConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "driftkit.rag.splitter", name = "type", havingValue = "recursive", matchIfMissing = true)
        public RecursiveCharacterTextSplitter recursiveCharacterTextSplitter(RagProperties properties) {
            RagProperties.SplitterProperties splitterProps = properties.getSplitter();
            
            log.info("Creating RecursiveCharacterTextSplitter with chunk size: {}, overlap: {}", 
                splitterProps.getChunkSize(), splitterProps.getChunkOverlap());
            
            return RecursiveCharacterTextSplitter.builder()
                .chunkSize(splitterProps.getChunkSize())
                .chunkOverlap(splitterProps.getChunkOverlap())
                .preserveMetadata(splitterProps.isPreserveMetadata())
                .addChunkMetadata(splitterProps.isAddChunkMetadata())
                .build();
        }
        
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "driftkit.rag.splitter", name = "type", havingValue = "semantic")
        @ConditionalOnBean(EmbeddingModel.class)
        public SemanticTextSplitter semanticTextSplitter(
                RagProperties properties,
                EmbeddingModel embeddingModel) {
            
            RagProperties.SplitterProperties splitterProps = properties.getSplitter();
            
            log.info("Creating SemanticTextSplitter with target size: {}, similarity threshold: {}", 
                splitterProps.getTargetChunkSize(), splitterProps.getSimilarityThreshold());
            
            return SemanticTextSplitter.builder()
                .embeddingModel(embeddingModel)
                .targetChunkSize(splitterProps.getTargetChunkSize())
                .similarityThreshold(splitterProps.getSimilarityThreshold())
                .maxChunkSize(splitterProps.getMaxChunkSize())
                .minChunkSize(splitterProps.getMinChunkSize())
                .preserveMetadata(splitterProps.isPreserveMetadata())
                .addChunkMetadata(splitterProps.isAddChunkMetadata())
                .build();
        }
    }
    
    /**
     * Configuration for reranker.
     */
    @Configuration
    @ConditionalOnClass(ModelBasedReranker.class)
    public static class RerankerConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "driftkit.rag.reranker", name = "enabled", havingValue = "true", matchIfMissing = true)
        @ConditionalOnBean({ModelClient.class, PromptService.class})
        public ModelBasedReranker modelBasedReranker(
                RagProperties properties,
                ModelClient modelClient,
                PromptService promptService) {
            
            RagProperties.RerankerProperties rerankerProps = properties.getReranker();
            
            log.info("Creating ModelBasedReranker with model: {}", rerankerProps.getModel());
            
            return ModelBasedReranker.builder()
                .modelClient(modelClient)
                .promptService(promptService)
                .promptId(rerankerProps.getPromptId())
                .model(rerankerProps.getModel())
                .temperature(rerankerProps.getTemperature())
                .build();
        }
    }
    
    /**
     * Configuration for retriever.
     */
    @Configuration
    @ConditionalOnClass(VectorStoreRetriever.class)
    public static class RetrieverConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(BaseVectorStore.class)
        public VectorStoreRetriever vectorStoreRetriever(
                RagProperties properties,
                BaseVectorStore vectorStore,
                @Autowired(required = false) EmbeddingModel embeddingModel) {
            
            RagProperties.RetrieverProperties retrieverProps = properties.getRetriever();
            
            log.info("Creating VectorStoreRetriever with query prefix: {}", retrieverProps.getQueryPrefix());
            
            return VectorStoreRetriever.builder()
                .vectorStore(vectorStore)
                .embeddingModel(embeddingModel)
                .queryPrefix(retrieverProps.getQueryPrefix())
                .build();
        }
    }
    
    /**
     * Main RAG service.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "driftkit.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RagService ragService(
            RagProperties properties,
            DocumentLoaderFactory loaderFactory,
            BaseVectorStore vectorStore,
            @Autowired(required = false) RecursiveCharacterTextSplitter textSplitter,
            @Autowired(required = false) EmbeddingModel embeddingModel,
            @Autowired(required = false) VectorStoreRetriever retriever,
            @Autowired(required = false) ModelBasedReranker reranker) {
        
        log.info("Creating RagService");
        
        return new RagService(
            properties,
            loaderFactory,
            vectorStore,
            textSplitter,
            embeddingModel,
            retriever,
            reranker
        );
    }
}