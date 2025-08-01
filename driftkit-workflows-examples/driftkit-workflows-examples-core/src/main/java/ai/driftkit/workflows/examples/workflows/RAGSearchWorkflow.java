package ai.driftkit.workflows.examples.workflows;

import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VaultConfig;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.service.EmbeddingFactory;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.DocumentsResult;
import ai.driftkit.vector.core.domain.EmbeddingVectorStore;
import ai.driftkit.vector.core.service.VectorStoreFactory;
import ai.driftkit.workflows.core.domain.*;
import ai.driftkit.workflows.spring.ModelWorkflow;
import ai.driftkit.workflows.spring.ModelRequestParams;
import ai.driftkit.workflows.examples.workflows.RAGSearchWorkflow.VectorStoreStartEvent;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.Response;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import java.nio.charset.Charset;
import java.util.*;

@Slf4j
public class RAGSearchWorkflow extends ModelWorkflow<VectorStoreStartEvent, DocumentsResult> {
    public static final String RAG_RERANK_METHOD = "rerank";

    private String queryPrefix;
    private EmbeddingModel embeddingModel;
    private EmbeddingVectorStore vectorStore;
    private VaultConfig modelConfig;

    public RAGSearchWorkflow(EtlConfig config, PromptService promptService, ModelRequestService modelRequestService) throws Exception {
        super(ModelClientFactory.fromConfig(config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
              modelRequestService,
              promptService);
              
        this.embeddingModel = EmbeddingFactory.fromName(
                config.getEmbedding().getName(),
                config.getEmbedding().getConfig()
        );
        this.vectorStore = (EmbeddingVectorStore) VectorStoreFactory.fromConfig(config.getVectorStore());

        this.queryPrefix = config.getEmbedding().get(EtlConfig.BASE_QUERY, "Instruct: Retrieve semantically similar text.\\nQuery: ");

        this.modelConfig = config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow();

        promptService.createIfNotExists(
                RAG_RERANK_METHOD,
                IOUtils.resourceToString("/prompts/dictionary/rag/%s.prompt".formatted(RAG_RERANK_METHOD), Charset.defaultCharset()),
                null,
                true,
                Language.GENERAL
        );
    }

    @Step
    @StepInfo(description = "First search request")
    public WorkflowEvent start(VectorStoreStartEvent startEvent, WorkflowContext workflowContext) throws Exception {
        String query = startEvent.getQuery();
        log.info("Executing start step with query: {}", query);

        Response<Embedding> embeddingResp = embeddingModel.embed(TextSegment.from(queryPrefix + query));
        float[] queryVector = embeddingResp.content().vector();

        DocumentsResult documents = vectorStore.findRelevant(startEvent.getIndexName(), queryVector, startEvent.getLimit());

        workflowContext.add("query", query);
        workflowContext.add("retrievedDocuments", documents);

        if (documents.isEmpty()) {
            return StopEvent.ofObject(documents);
        }

        return new DataEvent<>(documents, "rerank");
    }

    @Step
    @StepInfo(description = "Rerank request")
    public DataEvent<DocumentsResult> rerank(DataEvent<DocumentsResult> dataEvent, WorkflowContext workflowContext) throws Exception {
        DocumentsResult retrievedDocuments = dataEvent.getResult();
        List<String> query = workflowContext.get("query");

        DocumentsResult rerankedDocuments = rerankDocuments(retrievedDocuments, query.getFirst(), workflowContext);

        return new DataEvent<>(rerankedDocuments, "finalStep");
    }

    @Step
    @StepInfo(description = "Return results")
    public StopEvent<DocumentsResult> finalStep(DataEvent<DocumentsResult> event, WorkflowContext workflowContext) throws Exception {
        DocumentsResult finalDocuments = event.getResult();
        return StopEvent.ofObject(finalDocuments);
    }

    private DocumentsResult rerankDocuments(
            DocumentsResult retrievedDocuments,
            String query,
            WorkflowContext workflowContext) throws Exception {

        StringBuilder promptBuilder = new StringBuilder();
        Map<String, Document> docIdMap = new HashMap<>();
        
        for (Document doc : retrievedDocuments.documents()) {
            promptBuilder.append("Document ID ").append(doc.getId()).append(":\n");
            promptBuilder.append(doc.getPageContent()).append("\n\n");
            docIdMap.put(doc.getId(), doc);
        }

        String docsText = promptBuilder.toString();
        Map<String, Object> variables = Map.of("query", query, "documents", docsText);
        
        ModelTextResponse response = sendTextToText(
            ModelRequestParams.create()
                .setPromptId(RAG_RERANK_METHOD)
                .setVariables(variables), 
            workflowContext);
        String responseText = response.getResponse();

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Float> scoresMap;

        try {
            TypeReference<Map<String, Float>> typeRef = new TypeReference<>() {};
            scoresMap = objectMapper.readValue(responseText, typeRef);
        } catch (Exception e) {
            throw new Exception("Failed to parse JSON from model response: " + responseText, e);
        }

        DocumentsResult rerankedDocuments = new DocumentsResult();
        scoresMap.entrySet().stream()
                .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                .forEach(entry -> {
                    Document doc = docIdMap.get(entry.getKey());
                    if (doc != null) {
                        rerankedDocuments.put(doc, entry.getValue());
                    }
                });

        return rerankedDocuments;
    }

    @Data
    public static class VectorStoreStartEvent extends StartQueryEvent {
        private String indexName;
        private int limit;

        @Builder
        public VectorStoreStartEvent(String indexName, String query, int limit) {
            super(query);
            this.indexName = indexName;
            this.limit = limit;
        }
    }
}