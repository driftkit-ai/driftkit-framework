package ai.driftkit.vector.springai;

import ai.driftkit.config.EtlConfig.VectorStoreConfig;
import ai.driftkit.vector.core.domain.Document;
import ai.driftkit.vector.core.domain.DocumentsResult;
import ai.driftkit.vector.core.domain.TextVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter that bridges Spring AI VectorStore with DriftKit VectorStore interface.
 * This allows using any Spring AI vector store implementation within the DriftKit framework.
 */
@Slf4j
public class SpringAiVectorStoreAdapter implements TextVectorStore {
    
    private final org.springframework.ai.vectorstore.VectorStore springAiVectorStore;
    private final String storeName;
    private VectorStoreConfig config;
    
    public SpringAiVectorStoreAdapter(org.springframework.ai.vectorstore.VectorStore springAiVectorStore, String storeName) {
        if (springAiVectorStore == null) {
            throw new IllegalArgumentException("Spring AI VectorStore cannot be null");
        }
        if (StringUtils.isBlank(storeName)) {
            throw new IllegalArgumentException("Store name cannot be blank");
        }
        
        this.springAiVectorStore = springAiVectorStore;
        this.storeName = storeName;
    }
    
    @Override
    public void configure(VectorStoreConfig config) throws Exception {
        this.config = config;
        log.info("Configured Spring AI Vector Store adapter with store name: {}", storeName);
    }
    
    @Override
    public boolean supportsStoreName(String storeName) {
        return this.storeName.equalsIgnoreCase(storeName);
    }
    
    @Override
    public List<String> addDocuments(String index, List<Document> documents) throws Exception {
        if (CollectionUtils.isEmpty(documents)) {
            return new ArrayList<>();
        }
        
        if (StringUtils.isBlank(index)) {
            throw new IllegalArgumentException("Index cannot be blank");
        }
        
        List<org.springframework.ai.document.Document> springAiDocs = new ArrayList<>();
        List<String> documentIds = new ArrayList<>();
        
        for (Document doc : documents) {
            if (doc == null) {
                continue;
            }
            
            String id = StringUtils.isNotEmpty(doc.getId()) ? doc.getId() : UUID.randomUUID().toString();
            documentIds.add(id);
            
            Map<String, Object> metadata = new HashMap<>();
            if (doc.getMetadata() != null) {
                metadata.putAll(doc.getMetadata());
            }
            metadata.put("index", index);
            metadata.put("driftkit_id", id);
            
            org.springframework.ai.document.Document springAiDoc = org.springframework.ai.document.Document.builder()
                    .id(id)
                    .text(doc.getPageContent())
                    .metadata(metadata)
                    .build();
            
            springAiDocs.add(springAiDoc);
        }
        
        if (CollectionUtils.isEmpty(springAiDocs)) {
            return documentIds;
        }
        
        springAiVectorStore.add(springAiDocs);
        log.debug("Added {} documents to Spring AI vector store for index: {}", springAiDocs.size(), index);
        
        return documentIds;
    }
    
    @Override
    public DocumentsResult findRelevant(String index, String query, int k) throws Exception {
        if (StringUtils.isBlank(query)) {
            return DocumentsResult.EMPTY;
        }
        
        if (StringUtils.isBlank(index)) {
            throw new IllegalArgumentException("Index cannot be blank");
        }
        
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }

        // Create search request with text query
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .filterExpression(String.format("index == '%s'", escapeFilterValue(index)))
                .topK(k)
                .build();
        
        List<org.springframework.ai.document.Document> results = springAiVectorStore.similaritySearch(searchRequest);
        
        DocumentsResult result = new DocumentsResult();
        
        // Spring AI returns results already sorted by relevance
        for (org.springframework.ai.document.Document doc : results) {
            Document driftkitDoc = convertToDriftKitDocument(doc);
            if (driftkitDoc == null) {
                continue;
            }
            
            // Spring AI doesn't expose similarity scores directly, so we use a placeholder
            // The results are already ordered by relevance
            float score = -1f;
            
            result.put(driftkitDoc, score);
        }
        
        log.debug("Found {} relevant documents for index: {}", result.size(), index);
        return result;
    }
    
    @Override
    public void updateDocument(String id, String index, Document document) throws Exception {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Document ID cannot be empty");
        }
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (StringUtils.isBlank(index)) {
            throw new IllegalArgumentException("Index cannot be blank");
        }
        
        deleteDocument(id, index);
        document.setId(id);
        addDocuments(index, List.of(document));
        log.debug("Updated document {} in index: {}", id, index);
    }
    
    @Override
    public void deleteDocument(String id, String index) throws Exception {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Document ID cannot be empty");
        }
        if (StringUtils.isBlank(index)) {
            throw new IllegalArgumentException("Index cannot be blank");
        }
        
        springAiVectorStore.delete(List.of(id));
        log.debug("Deleted document {} from index: {}", id, index);
    }
    
    @Override
    public Document readDocument(String id, String index) throws Exception {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Document ID cannot be empty");
        }
        if (StringUtils.isBlank(index)) {
            throw new IllegalArgumentException("Index cannot be blank");
        }
        
        // For findById, we need to create a dummy query with proper filter
        SearchRequest searchRequest = SearchRequest.builder()
                .filterExpression(String.format("driftkit_id == '%s' && index == '%s'", escapeFilterValue(id), escapeFilterValue(index)))
                .topK(1)
                .build();
        
        List<org.springframework.ai.document.Document> results = springAiVectorStore.similaritySearch(searchRequest);
        
        if (CollectionUtils.isEmpty(results)) {
            return null;
        }
        
        return convertToDriftKitDocument(results.get(0));
    }
    
    private List<Double> convertToDoubleList(float[] floatArray) {
        if (floatArray == null) {
            return new ArrayList<>();
        }
        
        List<Double> doubleList = new ArrayList<>(floatArray.length);
        for (float f : floatArray) {
            doubleList.add((double) f);
        }
        return doubleList;
    }
    
    private float[] convertToFloatArray(List<Double> doubleList) {
        if (CollectionUtils.isEmpty(doubleList)) {
            return null;
        }
        
        float[] floatArray = new float[doubleList.size()];
        for (int i = 0; i < doubleList.size(); i++) {
            floatArray[i] = doubleList.get(i).floatValue();
        }
        return floatArray;
    }
    
    private Document convertToDriftKitDocument(org.springframework.ai.document.Document springAiDoc) {
        if (springAiDoc == null) {
            return null;
        }
        
        String id = (String) springAiDoc.getMetadata().getOrDefault("driftkit_id", springAiDoc.getId());
        
        Map<String, Object> metadata = new HashMap<>();
        if (springAiDoc.getMetadata() != null) {
            metadata.putAll(springAiDoc.getMetadata());
            metadata.remove("driftkit_id");
            metadata.remove("index");
        }
        
        return new Document(
                id,
                null,
                springAiDoc.getText(),
                metadata
        );
    }
    
    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }
    
    private float calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) {
            return 0.0f;
        }
        
        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        
        if (normA == 0.0f || normB == 0.0f) {
            return 0.0f;
        }
        
        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}