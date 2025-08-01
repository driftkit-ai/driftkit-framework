package ai.driftkit.embedding.springai;

import ai.driftkit.common.utils.SimpleTokenizer;
import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.local.AIOnnxBertBiEncoder;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapter that bridges Spring AI EmbeddingModel with DriftKit EmbeddingModel interface.
 * This allows using any Spring AI embedding model implementation within the DriftKit framework.
 */
@Slf4j
public class SpringAiEmbeddingAdapter implements EmbeddingModel {
    
    private final org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel;
    private final String modelName;
    private final SimpleTokenizer tokenizer = new SimpleTokenizer();
    
    public SpringAiEmbeddingAdapter(org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel, String modelName) {
        if (springAiEmbeddingModel == null) {
            throw new IllegalArgumentException("Spring AI EmbeddingModel cannot be null");
        }
        if (StringUtils.isBlank(modelName)) {
            throw new IllegalArgumentException("Model name cannot be blank");
        }
        
        this.springAiEmbeddingModel = springAiEmbeddingModel;
        this.modelName = modelName;
    }
    
    @Override
    public boolean supportsName(String name) {
        return this.modelName.equalsIgnoreCase(name);
    }
    
    @Override
    public AIOnnxBertBiEncoder model() {
        throw new UnsupportedOperationException(
            "SpringAiEmbeddingAdapter does not support local ONNX models. " +
            "It uses remote embedding services through Spring AI providers."
        );
    }
    
    @Override
    public void configure(EmbeddingServiceConfig config) {
        // Configuration is handled by Spring AI itself
        log.info("Configured Spring AI Embedding adapter with model name: {}", modelName);
    }
    
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        if (CollectionUtils.isEmpty(segments)) {
            log.debug("Empty segments provided, returning empty embeddings");
            return Response.from(Collections.emptyList());
        }
        
        // Extract texts and estimate tokens
        List<String> texts = new ArrayList<>();
        int totalEstimatedTokens = 0;
        
        for (TextSegment segment : segments) {
            if (segment == null || StringUtils.isEmpty(segment.text())) {
                log.warn("Skipping null or empty segment");
                continue;
            }
            texts.add(segment.text());
            totalEstimatedTokens += estimateTokenCount(segment.text());
        }
        
        if (texts.isEmpty()) {
            log.debug("No valid texts to embed, returning empty embeddings");
            return Response.from(Collections.emptyList());
        }
        
        try {
            log.debug("Embedding {} text segments", texts.size());
            
            // Create embedding request
            EmbeddingRequest request = new EmbeddingRequest(texts, null);
            
            // Call Spring AI model
            EmbeddingResponse springAiResponse = springAiEmbeddingModel.embedForResponse(texts);
            
            if (springAiResponse == null || springAiResponse.getResults() == null) {
                throw new RuntimeException("Received null response from Spring AI embedding model");
            }
            
            // Convert Spring AI embeddings to DriftKit embeddings
            List<Embedding> embeddings = new ArrayList<>();
            for (org.springframework.ai.embedding.Embedding springAiEmbedding : springAiResponse.getResults()) {
                if (springAiEmbedding == null || springAiEmbedding.getOutput() == null) {
                    log.warn("Received null embedding from Spring AI, skipping");
                    continue;
                }
                
                float[] floatArray = springAiEmbedding.getOutput();
                embeddings.add(Embedding.from(floatArray));
            }
            
            // Extract token usage if available
            TokenUsage tokenUsage = extractTokenUsage(springAiResponse, totalEstimatedTokens);
            
            log.debug("Successfully embedded {} segments", embeddings.size());
            return Response.from(embeddings, tokenUsage);
            
        } catch (Exception e) {
            log.error("Failed to generate embeddings", e);
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Response<Embedding> embed(TextSegment segment) {
        if (segment == null) {
            throw new IllegalArgumentException("TextSegment cannot be null");
        }
        
        Response<List<Embedding>> response = embedAll(List.of(segment));
        List<Embedding> embeddings = response.content();
        
        if (CollectionUtils.isEmpty(embeddings)) {
            log.warn("No embeddings generated for segment");
            return null;
        }
        
        return Response.from(embeddings.get(0), response.tokenUsage());
    }
    
    @Override
    public int estimateTokenCount(String text) {
        if (StringUtils.isEmpty(text)) {
            return 0;
        }
        
        // Use SimpleTokenizer's logic for consistent token estimation
        return (int) (text.length() * SimpleTokenizer.DEFAULT_TOKEN_COST);
    }
    
    /**
     * Extracts token usage from Spring AI response.
     * 
     * @param response the Spring AI embedding response
     * @param estimatedTokens the estimated token count
     * @return TokenUsage object
     */
    private TokenUsage extractTokenUsage(EmbeddingResponse response, int estimatedTokens) {
        try {
            if (response.getMetadata() != null && 
                response.getMetadata().getUsage() != null) {
                
                Integer totalTokens = response.getMetadata().getUsage().getTotalTokens();
                if (totalTokens != null && totalTokens > 0) {
                    return new TokenUsage(totalTokens);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract token usage from response, using estimated count", e);
        }
        
        // Fall back to estimated tokens
        return new TokenUsage(estimatedTokens);
    }
}