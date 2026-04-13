package /*PACKAGE_NAME*/.service;

import ai.driftkit.context.engineering.domain.Language;
import ai.driftkit.spring.ai.DriftKitChatClient;
import ai.driftkit.spring.ai.DriftKitPromptProvider;
import /*PACKAGE_NAME*/.controller.AIController.RAGResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {
    
    private final DriftKitChatClient driftKitChatClient;
    private final ChatClient chatClient;
    private final DriftKitPromptProvider promptProvider;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final EmbeddingClient embeddingClient;
    
    public String chat(String message, String sessionId, Map<String, Object> options) {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        
        // Use DriftKit-enhanced ChatClient for conversation with memory
        ChatResponse response = driftKitChatClient
            .prompt()
            .user(message)
            .advisors(advisor -> advisor
                .param("sessionId", sessionId)
                .param("memoryEnabled", true))
            .call();
        
        // Store in chat memory
        chatMemory.add(sessionId, response);
        
        return response.getResult().getOutput().getContent();
    }
    
    public String generateWithPrompt(String promptId, Map<String, Object> variables, String language) {
        Language lang = Language.valueOf(language.toUpperCase());
        
        // Use DriftKit prompts with Spring AI
        return driftKitChatClient
            .promptById(promptId)
            .withVariables(variables)
            .withLanguage(lang)
            .call()
            .content();
    }
    
    public Object generateStructuredOutput(String promptId, Map<String, Object> variables, String outputClassName) {
        try {
            Class<?> outputClass = Class.forName(outputClassName);
            
            // Get prompt configuration from DriftKit
            var promptConfig = promptProvider.getPrompt(promptId, Language.ENGLISH);
            
            // Use Spring AI with structured output
            return chatClient.prompt()
                .system(promptConfig.getSystemMessage())
                .user(u -> u.text(promptConfig.getUserMessage()).params(variables))
                .call()
                .entity(outputClass);
                
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Invalid output class: " + outputClassName, e);
        }
    }
    
    public RAGResponse ragQuery(String query, int maxResults, boolean includeSources) {
        // Search vector store
        List<Document> similarDocuments = vectorStore.similaritySearch(
            SearchRequest.query(query)
                .withTopK(maxResults)
                .withSimilarityThreshold(0.7)
        );
        
        if (similarDocuments.isEmpty()) {
            return new RAGResponse(
                "No relevant documents found for your query.",
                List.of(),
                0.0
            );
        }
        
        // Build context from documents
        String context = similarDocuments.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));
        
        // Generate answer using RAG prompt
        String answer = driftKitChatClient
            .promptById("rag.answer")
            .withVariable("context", context)
            .withVariable("question", query)
            .call()
            .content();
        
        // Build response with sources
        List<RAGResponse.Source> sources = includeSources ? 
            buildSources(similarDocuments) : List.of();
        
        double avgScore = similarDocuments.stream()
            .mapToDouble(doc -> (double) doc.getMetadata().getOrDefault("score", 1.0))
            .average()
            .orElse(0.0);
        
        return new RAGResponse(answer, sources, avgScore);
    }
    
    private List<RAGResponse.Source> buildSources(List<Document> documents) {
        return documents.stream()
            .map(doc -> new RAGResponse.Source(
                truncate(doc.getContent(), 200),
                (double) doc.getMetadata().getOrDefault("score", 1.0),
                doc.getMetadata()
            ))
            .collect(Collectors.toList());
    }
    
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}