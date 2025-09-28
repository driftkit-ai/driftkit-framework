package ai.driftkit.rag.core.reranker;

import ai.driftkit.common.domain.client.ModelClient;
import ai.driftkit.common.domain.client.ModelTextRequest;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.ResponseFormat;
import ai.driftkit.common.domain.client.Role;
import ai.driftkit.common.domain.client.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement;
import ai.driftkit.common.domain.client.ModelTextRequest.MessageType;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.context.core.util.DefaultPromptLoader;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.rag.core.retriever.Retriever.RetrievalResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Reranker that uses a language model to rerank documents based on relevance.
 * Uses structured output for reliable parsing.
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class ModelBasedReranker implements Reranker {
    
    @NonNull
    private final ModelClient modelClient;
    
    @NonNull
    private final PromptService promptService;
    
    @Builder.Default
    private final String promptId = "rag.rerank";
    
    @Builder.Default
    private final String model = "gpt-4o";
    
    @Builder.Default
    private final float temperature = 0.0f;
    
    /**
     * Structured output class for reranking scores.
     */
    @Data
    public static class RerankingScores {
        @JsonProperty(value = "document_scores", required = true)
        @JsonPropertyDescription("Map of document IDs to relevance scores (0.0 to 1.0)")
        private Map<String, Float> documentScores;
    }
    
    
    /**
     * Rerank documents based on the query.
     */
    @Override
    public List<RerankResult> rerank(String query, List<RetrievalResult> results, RerankConfig config) throws Exception {
        if (results.isEmpty()) {
            return List.of();
        }
        
        log.debug("Reranking {} documents for query: {}", results.size(), query);
        
        // Prepare documents text for the prompt
        StringBuilder documentsText = new StringBuilder();
        Map<String, RetrievalResult> resultMap = new HashMap<>();
        
        int docIndex = 0;
        for (RetrievalResult result : results) {
            String docId = "doc" + docIndex++;
            documentsText.append("Document ID ").append(docId).append(":\n");
            documentsText.append(result.document().getPageContent()).append("\n\n");
            resultMap.put(docId, result);
        }
        
        // Prepare variables for prompt
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query);
        variables.put("documents", documentsText.toString());
        
        // Load prompt using DefaultPromptLoader
        String finalPrompt = DefaultPromptLoader.loadPrompt(promptId, variables);
        
        // Create request with structured output
        ModelTextRequest request = ModelTextRequest.builder()
            .messages(List.of(
                ModelContentMessage.builder()
                    .role(Role.user)
                    .content(List.of(
                        ModelContentElement.builder()
                            .type(MessageType.text)
                            .text(finalPrompt)
                            .build()
                    ))
                    .build()
            ))
            .model(model)
            .temperature((double) temperature)
            .responseFormat(ResponseFormat.jsonSchema(RerankingScores.class))
            .build();
            
        ModelTextResponse response = modelClient.textToText(request);
        
        // Parse the structured response
        String responseContent = response.getChoices().get(0).getMessage().getContent();
        RerankingScores rerankingScores = JsonUtils.fromJson(responseContent, RerankingScores.class);
        
        if (rerankingScores == null || rerankingScores.getDocumentScores() == null) {
            log.error("Failed to parse reranking response: {}", responseContent);
            throw new RuntimeException("Invalid reranking response");
        }
        
        Map<String, Float> scores = rerankingScores.getDocumentScores();
        
        // Create reranked results
        List<RerankResult> rerankedResults = new ArrayList<>();
        
        for (Map.Entry<String, Float> entry : scores.entrySet()) {
            RetrievalResult originalResult = resultMap.get(entry.getKey());
            if (originalResult != null) {
                // Create RerankResult with both original and rerank scores
                RerankResult rerankedResult = RerankResult.from(originalResult, entry.getValue());
                rerankedResults.add(rerankedResult);
            }
        }
        
        // Sort by rerank score descending
        rerankedResults.sort((a, b) -> Float.compare(b.rerankScore(), a.rerankScore()));
        
        // Apply topK limit from config
        int limit = config.topK() > 0 ? Math.min(config.topK(), rerankedResults.size()) : rerankedResults.size();
        
        List<RerankResult> finalResults = rerankedResults.subList(0, limit);
        
        log.debug("Reranking complete. Returning {} documents", finalResults.size());
        
        return finalResults;
    }
}