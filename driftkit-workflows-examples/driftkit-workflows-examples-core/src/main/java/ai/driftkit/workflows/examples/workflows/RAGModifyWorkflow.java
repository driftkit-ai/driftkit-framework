package ai.driftkit.workflows.examples.workflows;

import ai.driftkit.clients.core.ModelClientFactory;
import ai.driftkit.clients.openai.client.OpenAIModelClient;
import ai.driftkit.config.EtlConfig;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.embedding.core.service.EmbeddingFactory;
import ai.driftkit.vector.core.domain.EmbeddingVectorStore;
import ai.driftkit.vector.core.service.VectorStoreFactory;
import ai.driftkit.vector.spring.domain.ParsedContent;
import ai.driftkit.vector.spring.parser.UnifiedParser;
import ai.driftkit.vector.spring.parser.UnifiedParser.ByteArrayParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.ParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.StringParserInput;
import ai.driftkit.vector.spring.parser.UnifiedParser.YoutubeIdParserInput;
import ai.driftkit.workflows.core.domain.*;
import ai.driftkit.workflows.spring.ModelWorkflow;
import ai.driftkit.workflows.spring.ModelRequestParams;
import ai.driftkit.context.core.service.PromptService;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.common.utils.DocumentSplitter;
import ai.driftkit.workflows.spring.service.ModelRequestService;
import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class RAGModifyWorkflow extends ModelWorkflow<StartEvent, RAGModifyWorkflow.DocumentSaveResult> {

    private ThreadPoolExecutor exec;
    private UnifiedParser parser;
    private EmbeddingModel embeddingModel;
    private EmbeddingVectorStore vectorStore;

    public RAGModifyWorkflow(EtlConfig config, PromptService promptService, ModelRequestService modelRequestService) throws Exception {
        super(ModelClientFactory.fromConfig(config.getModelConfig(OpenAIModelClient.OPENAI_PREFIX).orElseThrow()),
              modelRequestService,
              promptService);
              
        this.parser = new UnifiedParser(config);
        this.embeddingModel = EmbeddingFactory.fromName(
                config.getEmbedding().getName(),
                config.getEmbedding().getConfig()
        );
        this.vectorStore = (EmbeddingVectorStore) VectorStoreFactory.fromConfig(config.getVectorStore());

        Integer storingThreads = config.getVectorStore().getInt(EtlConfig.VECTOR_STORE_STORING_THREADS, 1);

        this.exec = new ThreadPoolExecutor(
                0,
                storingThreads,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Step
    @StepInfo(description = "Parse input")
    public DataEvent<ParsedContent> parseInput(DocumentsEvent startEvent, WorkflowContext workflowContext) throws Exception {
        ParserInput document = startEvent.getInput();

        ParsedContent parsed = parser.parse(document);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("contentType", document.getContentType());
        metadata.put("parsedResultLength", parsed.getParsedContent().length());

        if (document instanceof YoutubeIdParserInput youtubeInput) {
            metadata.put("videoId", youtubeInput.getVideoId());

            List<String> languages = new ArrayList<>();
            languages.add(youtubeInput.getPrimaryLang());

            if (CollectionUtils.isNotEmpty(youtubeInput.getInput())) {
                languages.addAll(youtubeInput.getInput());
            }

            metadata.put("languages", languages);
        } else if (document instanceof ByteArrayParserInput byteInput) {
            metadata.put("fileSize", byteInput.getInput().length);
            metadata.put("fileName", byteInput.getFileName());
        } else if (document instanceof StringParserInput stringInput) {
            metadata.put("stringHash", PromptUtils.hashString(stringInput.getInput()));
            metadata.put("stringLength", stringInput.getInput().length());
        }

        workflowContext.put("metadata", metadata);
        workflowContext.put("index", startEvent.getIndex());

        return new DataEvent<>(parsed, "ingestDocument");
    }

    @Step
    @StepInfo(description = "Ingest documents and store them into the vector store")
    public StopEvent<DocumentSaveResult> ingestDocument(DataEvent<ParsedContent> documentEvent, WorkflowContext workflowContext) throws Exception {
        String index = workflowContext.get("index");
        Map<String, Object> metadata = workflowContext.get("metadata");

        ParsedContent parsed = documentEvent.getResult();

        int chunkSize = 512;
        int overlap = 128;

        List<String> chunks = DocumentSplitter.splitDocumentIntoShingles(parsed.getParsedContent(), chunkSize, overlap);

        String id = parsed.getId();

        log.info("Ingesting documents: {}", id);

        metadata.put("totalChunks", chunks.size());

        AtomicInteger counter = new AtomicInteger();

        List<Callable<Document>> tasks = chunks.stream()
                .map(chunk -> (Callable<Document>) () -> {
                    Response<Embedding> embeddingResp = embeddingModel.embed(TextSegment.from(chunk));
                    float[] vector = embeddingResp.content().vector();

                    Map<String, Object> newMeta = new HashMap<>(metadata);
                    int idx = counter.incrementAndGet();
                    newMeta.put("docIndex", idx);

                    Map<String, Object> currentStatus = new HashMap<>(metadata);
                    currentStatus.put("totalChunksProcessed", idx);
                    workflowContext.put("metadata", currentStatus);

                    return new Document(id + "-" + idx, vector, chunk, newMeta);
                })
                .collect(Collectors.toList());

        List<Future<Document>> futures = exec.invokeAll(tasks);

        List<Document> docsToAdd = new ArrayList<>();
        for (Future<Document> future : futures) {
            docsToAdd.add(future.get());
        }

        workflowContext.put("metadata", metadata);

        log.info("Ingested documents: {}", docsToAdd.size());

        vectorStore.addDocuments(index, docsToAdd);

        return StopEvent.ofObject(new DocumentSaveResult(
                id,
                parsed
        ));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSaveResult {
        String id;
        ParsedContent content;
    }

    @Override
    public Class<DocumentSaveResult> getOutputType() {
        return DocumentSaveResult.class;
    }

    @Data
    @Builder
    public static class DocumentsEvent<T extends ParserInput<?>> extends StartEvent {
        private T input;
        private String index;

        @Builder
        public DocumentsEvent(T input, String index) {
            this.input = input;
            this.index = index;
        }
    }
}