package ai.driftkit.vector.core.inmemory;

import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.DocumentsResult;
import ai.driftkit.vector.core.domain.EmbeddingVectorStore;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryVectorStore implements EmbeddingVectorStore {

    protected Map<String, Map<String, Document>> documentMap = new ConcurrentHashMap<>();

    public boolean supportsStoreName(String storeName) {
        return "inmemory".equalsIgnoreCase(storeName);
    }

    @Override
    public void configure(VectorStoreConfig config) throws Exception {
    }

    public List<String> addDocuments(String indexName, List<Document> documents) {
        List<String> ids = new ArrayList<>();
        for (Document doc : documents) {
            String id = doc.getId();
            if (id == null || id.isEmpty()) {
                id = UUID.randomUUID().toString();
                doc.setId(id);
            }

            Map<String, Document> index = getIndexOrCreate(indexName);

            index.put(id, doc);
            ids.add(id);
        }
        return ids;
    }

    public DocumentsResult findRelevant(String index, float[] queryEmbedding, int topK) {
        return query(index, queryEmbedding, topK, null);
    }

    public DocumentsResult query(String indexName, float[] queryEmbedding, int topK, Map<String, Object> filters) {
        Map<String, Document> index = getIndexOrCreate(indexName);

        // Step 1: Apply metadata filters
        List<Document> filteredDocuments = index.values().stream()
                .filter(doc -> filters == null || (doc.getVector() != null && matchesFilters(doc.getMetadata(), filters)))
                .collect(Collectors.toList());

        // Step 2: Extract embeddings and IDs
        List<float[]> docVectors = filteredDocuments.stream()
                .map(Document::getVector)
                .collect(Collectors.toList());

        List<String> docIds = filteredDocuments.stream()
                .map(Document::getId)
                .collect(Collectors.toList());

        // Step 3: Calculate top-k similarities
        List<SimilarityResult> topResults = getTopKSimilarities(queryEmbedding, docVectors, docIds, topK);

        // Step 4: Prepare results
        LinkedHashMap<Document, Float> resultMap = new LinkedHashMap<>();
        for (SimilarityResult result : topResults) {
            if (result.getSimilarity() == 0) {
                continue;
            }

            Document doc = index.get(result.getDocumentId());
            resultMap.put(doc, result.getSimilarity());
        }

        return new DocumentsResult(resultMap);
    }

    private boolean matchesFilters(Map<String, Object> metadata, Map<String, Object> filters) {
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            if (!metadata.containsKey(filter.getKey()) || !metadata.get(filter.getKey()).equals(filter.getValue())) {
                return false;
            }
        }
        return true;
    }

    private List<SimilarityResult> getTopKSimilarities(float[] queryEmbedding, List<float[]> docEmbeddings, List<String> docIds, int k) {
        List<SimilarityResult> results = new ArrayList<>();
        for (int i = 0; i < docEmbeddings.size(); i++) {
            float[] docEmbedding = docEmbeddings.get(i);
            float similarity = cosineSimilarity(queryEmbedding, docEmbedding);
            results.add(new SimilarityResult(similarity, docIds.get(i)));
        }

        // Sort results by similarity in descending order
        results.sort((r1, r2) -> Float.compare(r2.getSimilarity(), r1.getSimilarity()));

        // Return top-k results
        return results.stream().filter(e -> e.getSimilarity() > 0).limit(k).collect(Collectors.toList());
    }

    private float cosineSimilarity(float[] v1, float[] v2) {
        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10f);
    }

    public void updateDocument(String id, String indexName, Document document) {
        Map<String, Document> index = getIndexOrCreate(indexName);

        if (!index.containsKey(id)) {
            throw new NoSuchElementException("No document found with ID: " + id);
        }
        index.put(id, document);
    }

    public void deleteDocument(String id, String indexName) {
        Map<String, Document> index = getIndexOrCreate(indexName);

        if (!index.containsKey(id)) {
            throw new NoSuchElementException("No document found with ID: " + id);
        }

        index.remove(id);
    }

    public Document readDocument(String id, String indexName) {
        return getIndexOrCreate(indexName).get(id);
    }

    @NotNull
    private Map<String, Document> getIndexOrCreate(String indexName) {
        return documentMap.computeIfAbsent(indexName, e -> new ConcurrentHashMap<>());
    }

    private static class SimilarityResult {
        private final float similarity;
        private final String documentId;

        public SimilarityResult(float similarity, String documentId) {
            this.similarity = similarity;
            this.documentId = documentId;
        }

        public float getSimilarity() {
            return similarity;
        }

        public String getDocumentId() {
            return documentId;
        }
    }
}