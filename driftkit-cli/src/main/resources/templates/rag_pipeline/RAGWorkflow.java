package /*PACKAGE_NAME*/.workflow;

import ai.driftkit.clients.core.ModelClient;
import ai.driftkit.common.domain.Document;
import ai.driftkit.rag.retriever.VectorStoreRetriever;
import ai.driftkit.workflows.domain.WorkflowRequest;
import ai.driftkit.workflows.domain.WorkflowResponse;
import ai.driftkit.workflows.workflow.ExecutableWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RAGWorkflow extends ExecutableWorkflow {
    
    private final VectorStoreRetriever retriever;
    private final ModelClient modelClient;
    
    @Override
    public String getWorkflowId() {
        return "rag-qa";
    }
    
    @Override
    public String getDescription() {
        return "Retrieval-Augmented Generation workflow for question answering";
    }
    
    @Override
    public WorkflowResponse execute(WorkflowRequest request) {
        String question = request.getInput();
        log.info("Executing RAG workflow for question: {}", question);
        
        // Step 1: Retrieve relevant documents
        List<Document> relevantDocs = retriever.retrieve(question, 5);
        
        if (relevantDocs.isEmpty()) {
            return WorkflowResponse.builder()
                .output("I couldn't find any relevant information to answer your question.")
                .metadata(Map.of("documentsFound", 0))
                .build();
        }
        
        // Step 2: Build context from retrieved documents
        String context = buildContext(relevantDocs);
        
        // Step 3: Generate answer using LLM
        String prompt = buildPrompt(question, context);
        String answer = modelClient.generateText(prompt);
        
        // Step 4: Build response with metadata
        return WorkflowResponse.builder()
            .output(answer)
            .metadata(Map.of(
                "documentsFound", relevantDocs.size(),
                "sources", buildSourcesList(relevantDocs),
                "averageRelevanceScore", calculateAverageScore(relevantDocs)
            ))
            .build();
    }
    
    private String buildContext(List<Document> documents) {
        return documents.stream()
            .map(doc -> String.format("Source: %s\nContent: %s\n",
                doc.getMetadata().get("filename"),
                doc.getContent()))
            .collect(Collectors.joining("\n---\n"));
    }
    
    private String buildPrompt(String question, String context) {
        return String.format("""
            You are a helpful assistant that provides accurate answers based on the given context.
            Answer the question using only the information provided in the context.
            If the context doesn't contain enough information, acknowledge this limitation.
            
            Context:
            %s
            
            Question: %s
            
            Please provide a comprehensive answer:""", context, question);
    }
    
    private List<Map<String, Object>> buildSourcesList(List<Document> documents) {
        return documents.stream()
            .map(doc -> Map.of(
                "documentId", doc.getMetadata().getOrDefault("documentId", "unknown"),
                "filename", doc.getMetadata().getOrDefault("filename", "unknown"),
                "relevanceScore", doc.getMetadata().getOrDefault("score", 0.0)
            ))
            .collect(Collectors.toList());
    }
    
    private double calculateAverageScore(List<Document> documents) {
        return documents.stream()
            .mapToDouble(doc -> {
                Object score = doc.getMetadata().get("score");
                return score instanceof Number ? ((Number) score).doubleValue() : 0.0;
            })
            .average()
            .orElse(0.0);
    }
}