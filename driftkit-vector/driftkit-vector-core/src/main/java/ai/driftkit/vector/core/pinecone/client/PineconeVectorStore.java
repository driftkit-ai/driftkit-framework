package ai.driftkit.vector.core.pinecone.client;

import ai.driftkit.config.EtlConfig;
import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.DocumentsResult;
import ai.driftkit.vector.core.domain.EmbeddingVectorStore;
import ai.driftkit.vector.core.pinecone.client.PineconeVectorStore.PineconeQueryResponse.Match;
import ai.driftkit.vector.core.pinecone.client.PineconeVectorStore.PineconeUpsertRequest.VectorEntry;
import feign.*;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.collections4.CollectionUtils;

import java.nio.ByteBuffer;
import java.util.*;

@Slf4j
public class PineconeVectorStore implements EmbeddingVectorStore {
    private VectorStoreConfig config;
    private PineconeApi api;

    @Override
    public void configure(VectorStoreConfig config) {
        this.config = config;
        this.api = Feign.builder()
                .client(new feign.okhttp.OkHttpClient(new OkHttpClient.Builder().build()))
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .target(PineconeApi.class, config.get(EtlConfig.ENDPOINT));
        log.info("Configured PineconeVectorStore");
    }

    @Override
    public boolean supportsStoreName(String storeName) {
        return "pinecone".equalsIgnoreCase(storeName);
    }

    @Override
    public List<String> addDocuments(String index, List<Document> documents) throws Exception {
        String namespace = index;
        List<VectorEntry> entries = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (Document doc : documents) {
            String id = doc.getId();
            if (id == null || id.isEmpty()) {
                id = UUID.randomUUID().toString();
                doc.setId(id);
            }
            Map<String, Object> metadata = fromDocument(doc);
            entries.add(new VectorEntry(id, doc.getVector(), metadata));
            ids.add(id);
        }
        PineconeUpsertRequest req = new PineconeUpsertRequest(entries, namespace);
        api.upsert(config.get(EtlConfig.API_KEY), req);
        return ids;
    }

    @Override
    public DocumentsResult findRelevant(String index, float[] embedding, int k) throws Exception {
        String namespace = index;
        PineconeQueryRequest req = new PineconeQueryRequest(embedding, k, namespace, true);
        PineconeQueryResponse resp = api.query(config.get(EtlConfig.API_KEY), req);
        if (resp.getMatches() == null) return DocumentsResult.EMPTY;
        LinkedHashMap<Document, Float> resultMap = new LinkedHashMap<>();
        for (PineconeQueryResponse.Match m : resp.getMatches()) {
            Document doc = toDocument(m.getId(), m.getMetadata());
            resultMap.put(doc, m.getScore());
        }
        return new DocumentsResult(resultMap);
    }

    @Override
    public void updateDocument(String id, String index, Document document) throws Exception {
        try {
            String namespace = index;
            VectorEntry entry = new VectorEntry(id, document.getVector(), fromDocument(document));
            PineconeUpsertRequest req = new PineconeUpsertRequest(Collections.singletonList(entry), namespace);
            api.upsert(config.get(EtlConfig.API_KEY), req);
        } catch (Exception e) {
            if (e instanceof FeignException ex) {
                Optional<ByteBuffer> body = ex.responseBody();

                if (body.isPresent()) {
                    String bs = new String(body.get().array());

                    throw new RuntimeException(bs, e);
                }
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteDocument(String id, String index) throws Exception {
        String namespace = index;
        PineconeDeleteRequest deleteRequest = new PineconeDeleteRequest(Collections.singletonList(id), namespace, false, null);
        api.delete(config.get(EtlConfig.API_KEY), deleteRequest);
    }

    @Override
    public Document readDocument(String id, String index) throws Exception {
        PineconeIdQueryRequest queryParams = new PineconeIdQueryRequest();
        queryParams.setId(id);
        queryParams.setNamespace(index);
        queryParams.setTopK(1);
        queryParams.setIncludeMetadata(true);

        PineconeQueryResponse resp = api.fetch(config.get(EtlConfig.API_KEY), queryParams);

        if (CollectionUtils.isEmpty(resp.getMatches())) return null;

        Match m = resp.getMatches().getFirst();

        return toDocument(m.getId(), m.getMetadata());
    }

    private Document toDocument(String id, Map<String, Object> metadata) {
        String pageContent = (String) metadata.getOrDefault("page_content", "");
        Map<String, Object> metaCopy = new HashMap<>(metadata);
        metaCopy.remove("page_content");
        Document doc = new Document(id, null, pageContent);
        doc.setMetadata(metaCopy);
        return doc;
    }

    private Map<String, Object> fromDocument(Document doc) {
        Map<String, Object> metadata = new HashMap<>(
                doc.getMetadata() != null ? doc.getMetadata() : Collections.emptyMap()
        );
        metadata.put("page_content", doc.getPageContent());
        return metadata;
    }

    // Feign client interface
    public interface PineconeApi {

        @RequestLine("POST /vectors/upsert")
        @Headers({"Content-Type: application/json", "Api-Key: {apiKey}"})
        Map<String, Object> upsert(@Param("apiKey") String apiKey, PineconeUpsertRequest request);

        @RequestLine("POST /query")
        @Headers({"Content-Type: application/json", "Api-Key: {apiKey}"})
        PineconeQueryResponse query(@Param("apiKey") String apiKey, PineconeQueryRequest request);

        @RequestLine("POST /query")
        @Headers({"Content-Type: application/json", "Api-Key: {apiKey}"})
        PineconeQueryResponse fetch(@Param("apiKey") String apiKey, @QueryMap PineconeIdQueryRequest idRequest);

        @RequestLine("POST /vectors/delete")
        @Headers({"Content-Type: application/json", "Api-Key: {apiKey}"})
        Map<String, Object> delete(@Param("apiKey") String apiKey, PineconeDeleteRequest request);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PineconeUpsertRequest {
        private List<VectorEntry> vectors;
        private String namespace;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VectorEntry {
            private String id;
            private float[] values;
            private Map<String, Object> metadata;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PineconeQueryRequest {
        private float[] vector;
        private int topK;
        private String namespace;
        private boolean includeMetadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PineconeIdQueryRequest {
        private String id;
        private int topK;
        private String namespace;
        private boolean includeMetadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PineconeQueryResponse {
        private List<Match> matches;
        private String namespace;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Match {
            private String id;
            private float score;
            private Map<String, Object> metadata;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PineconeFetchResponse {
        private Map<String, VectorRecord> vectors;
        private String namespace;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class VectorRecord {
            private String id;
            private float[] values;
            private Map<String, Object> metadata;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PineconeDeleteRequest {
        private List<String> ids;
        private String namespace;
        private boolean deleteAll;
        private Map<String, Object> filter;
    }
}