package /*PACKAGE_NAME*/.service;

import ai.driftkit.clients.core.ModelClient;
import ai.driftkit.common.domain.Document;
import ai.driftkit.embedding.EmbeddingModel;
import ai.driftkit.rag.retriever.VectorStoreRetriever;
import /*PACKAGE_NAME*/.controller.DocumentController.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSearchService {
    
    private final VectorStoreRetriever retriever;
    private final ModelClient modelClient;
    private final EmbeddingModel embeddingModel;
    
    @Value("${driftkit.rag.retrieval.max-results:5}")
    private int defaultMaxResults;
    
    @Value("${driftkit.rag.retrieval.min-score:0.7}")
    private double defaultMinScore;
    
    public List<SearchResult> search(String query, int limit, double minScore) {
        log.debug("Searching for: {} (limit: {}, minScore: {})", query, limit, minScore);
        
        // Retrieve relevant documents
        List<Document> documents = retriever.retrieve(query, limit);
        
        // Filter by score and convert to response
        return documents.stream()
            .filter(doc -> getScore(doc) >= minScore)
            .map(this::toSearchResult)
            .collect(Collectors.toList());
    }
    
    public AnswerResponse answerQuestion(String question, int maxSources, boolean includeSources) {
        log.debug("Answering question: {}", question);
        
        // Retrieve relevant documents
        List<Document> relevantDocs = retriever.retrieve(question, maxSources * 2);
        
        if (relevantDocs.isEmpty()) {
            return new AnswerResponse(
                question,
                "I couldn't find any relevant information to answer your question.",
                List.of(),
                0.0
            );
        }
        
        // Build context from documents
        String context = buildContext(relevantDocs, maxSources);
        
        // Generate answer using the model
        String prompt = buildAnswerPrompt(question, context);
        String answer = modelClient.generateText(prompt);
        
        // Calculate confidence based on relevance scores
        double avgScore = relevantDocs.stream()
            .limit(maxSources)
            .mapToDouble(this::getScore)
            .average()
            .orElse(0.0);
        
        // Build sources if requested
        List<AnswerResponse.Source> sources = includeSources ? 
            buildSources(relevantDocs, maxSources) : List.of();
        
        return new AnswerResponse(question, answer, sources, avgScore);
    }
    
    private SearchResult toSearchResult(Document document) {
        return new SearchResult(
            (String) document.getMetadata().get("documentId"),
            document.getContent(),
            getScore(document),
            document.getMetadata()
        );
    }
    
    private double getScore(Document document) {
        Object score = document.getMetadata().get("score");
        if (score instanceof Number) {
            return ((Number) score).doubleValue();
        }
        return 1.0;
    }
    
    private String buildContext(List<Document> documents, int maxSources) {
        StringBuilder context = new StringBuilder();
        
        documents.stream()
            .limit(maxSources)
            .forEach(doc -> {
                context.append("---\n");
                context.append("Source: ").append(doc.getMetadata().get("filename")).append("\n");
                context.append("Content: ").append(doc.getContent()).append("\n");
                context.append("---\n\n");
            });
        
        return context.toString();
    }
    
    private String buildAnswerPrompt(String question, String context) {
        return String.format("""
            You are a helpful assistant that answers questions based on the provided context.
            Use only the information from the context to answer the question.
            If the context doesn't contain enough information, say so.
            
            Context:
            %s
            
            Question: %s
            
            Answer:""", context, question);
    }
    
    private List<AnswerResponse.Source> buildSources(List<Document> documents, int maxSources) {
        return documents.stream()
            .limit(maxSources)
            .map(doc -> new AnswerResponse.Source(
                (String) doc.getMetadata().get("documentId"),
                truncateContent(doc.getContent(), 200),
                getScore(doc)
            ))
            .collect(Collectors.toList());
    }
    
    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength - 3) + "...";
    }
}