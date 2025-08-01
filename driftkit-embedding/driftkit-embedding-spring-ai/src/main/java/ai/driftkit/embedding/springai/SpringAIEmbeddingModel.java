package ai.driftkit.embedding.springai;

import ai.driftkit.common.utils.SimpleTokenizer;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.EmbeddingServiceConfig;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.local.AIOnnxBertBiEncoder;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.embedding.core.domain.Response;
import ai.driftkit.embedding.core.domain.TokenUsage;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Spring AI implementation of the DriftKit EmbeddingModel interface.
 * This adapter allows using Spring AI's embedding models within the DriftKit framework.
 * 
 * Supported providers:
 * - OpenAI
 * - Azure OpenAI
 * - Ollama
 * 
 * The implementation handles:
 * - Model configuration and initialization
 * - Text to embedding conversion
 * - Token counting estimation
 * - Error handling and validation
 */
@Slf4j
@NoArgsConstructor
public class SpringAIEmbeddingModel implements EmbeddingModel {

    private org.springframework.ai.embedding.EmbeddingModel springAIModel;
    private String modelName;
    private SpringAIEmbeddingModelFactory modelFactory;
    private final SimpleTokenizer tokenizer = new SimpleTokenizer();

    /**
     * Checks if this implementation supports the given name.
     * 
     * @param name the name to check
     * @return true if name is "spring-ai", false otherwise
     */
    @Override
    public boolean supportsName(String name) {
        return "spring-ai".equals(name);
    }

    /**
     * Returns the underlying ONNX model.
     * 
     * @throws UnsupportedOperationException always, as Spring AI uses remote models
     */
    @Override
    public AIOnnxBertBiEncoder model() {
        throw new UnsupportedOperationException(
            "SpringAIEmbeddingModel does not support local ONNX models. " +
            "It uses remote embedding services through Spring AI providers."
        );
    }

    /**
     * Configures the embedding model with the provided configuration.
     * 
     * @param config the configuration containing provider and model details
     * @throws IllegalArgumentException if provider is missing or configuration is invalid
     * @throws RuntimeException if model creation fails
     */
    @Override
    public void configure(EmbeddingServiceConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        Map<String, String> configMap = config.getConfig();
        if (configMap == null) {
            throw new IllegalArgumentException("Configuration map cannot be null");
        }

        String provider = configMap.get("provider");
        this.modelName = configMap.get(EtlConfig.MODEL_NAME);
        
        if (StringUtils.isEmpty(provider)) {
            throw new IllegalArgumentException(
                "Spring AI provider must be specified in configuration. " +
                "Supported providers: openai, azure-openai, ollama"
            );
        }

        log.info("Configuring Spring AI embedding model with provider: {} and model: {}", 
                provider, modelName);
        
        try {
            // Initialize the factory and create the appropriate Spring AI model
            this.modelFactory = new SpringAIEmbeddingModelFactory();
            this.springAIModel = modelFactory.createEmbeddingModel(provider, configMap);
            log.info("Successfully configured Spring AI embedding model for provider: {}", provider);
        } catch (Exception e) {
            log.error("Failed to configure Spring AI embedding model", e);
            throw new RuntimeException("Failed to configure Spring AI embedding model: " + e.getMessage(), e);
        }
    }

    /**
     * Embeds multiple text segments into embeddings.
     * 
     * @param segments the text segments to embed
     * @return response containing embeddings and token usage
     * @throws IllegalStateException if model is not configured
     * @throws RuntimeException if embedding fails
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        if (springAIModel == null) {
            throw new IllegalStateException(
                "SpringAIEmbeddingModel not configured. Call configure() first."
            );
        }

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
            
            // Call Spring AI model
            EmbeddingResponse springAIResponse = springAIModel.embedForResponse(texts);
            
            if (springAIResponse == null || springAIResponse.getResults() == null) {
                throw new RuntimeException("Received null response from Spring AI embedding model");
            }
            
            // Convert Spring AI embeddings to DriftKit embeddings
            List<Embedding> embeddings = new ArrayList<>();
            for (org.springframework.ai.embedding.Embedding springAIEmbedding : springAIResponse.getResults()) {
                if (springAIEmbedding == null || springAIEmbedding.getOutput() == null) {
                    log.warn("Received null embedding from Spring AI, skipping");
                    continue;
                }
                
                float[] floatArray = springAIEmbedding.getOutput();
                embeddings.add(Embedding.from(floatArray));
            }

            // Extract token usage if available
            TokenUsage tokenUsage = extractTokenUsage(springAIResponse, totalEstimatedTokens);

            log.debug("Successfully embedded {} segments", embeddings.size());
            return Response.from(embeddings, tokenUsage);
            
        } catch (Exception e) {
            log.error("Failed to generate embeddings", e);
            throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
        }
    }

    /**
     * Embeds a single text segment.
     * 
     * @param segment the text segment to embed
     * @return response containing the embedding and token usage
     */
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

    /**
     * Estimates the token count for the given text.
     * 
     * @param text the text to estimate tokens for
     * @return estimated token count
     */
    @Override
    public int estimateTokenCount(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        
        // Use SimpleTokenizer's logic for consistent token estimation
        return (int) (text.length() * SimpleTokenizer.DEFAULT_TOKEN_COST);
    }

    /**
     * Converts a list of Double values to a float array.
     * 
     * @param doubleList the list of Double values
     * @return float array
     */
    private float[] convertToFloatArray(List<Double> doubleList) {
        if (doubleList == null) {
            throw new IllegalArgumentException("Double list cannot be null");
        }
        
        float[] floatArray = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            Double value = doubleList.get(i);
            if (value == null) {
                throw new IllegalArgumentException("Embedding contains null value at index " + i);
            }
            floatArray[i] = value.floatValue();
        }
        return floatArray;
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
                    return new TokenUsage(totalTokens.intValue());
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract token usage from response, using estimated count", e);
        }
        
        // Fall back to estimated tokens
        return new TokenUsage(estimatedTokens);
    }
}