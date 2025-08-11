package ai.driftkit.rag.core.splitter;

import ai.driftkit.embedding.core.domain.Embedding;
import ai.driftkit.embedding.core.domain.Response;
import ai.driftkit.embedding.core.domain.TextSegment;
import ai.driftkit.embedding.core.service.EmbeddingModel;
import ai.driftkit.rag.core.domain.LoadedDocument;
import ai.driftkit.vector.core.domain.Document;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.text.BreakIterator;
import java.util.*;

/**
 * Text splitter that uses semantic similarity to create chunks.
 * Groups sentences together based on their semantic similarity.
 */
@Slf4j
@Builder
@RequiredArgsConstructor
public class SemanticTextSplitter implements TextSplitter {
    
    @NonNull
    private final EmbeddingModel embeddingModel;
    
    @Builder.Default
    private final int targetChunkSize = 512;
    
    @Builder.Default
    private final float similarityThreshold = 0.7f;
    
    @Builder.Default
    private final int maxChunkSize = 1024;
    
    @Builder.Default
    private final int minChunkSize = 100;
    
    @Builder.Default
    private final boolean preserveMetadata = true;
    
    @Builder.Default
    private final boolean addChunkMetadata = true;
    
    /**
     * Split a loaded document into semantically coherent chunks.
     */
    @Override
    public List<Document> split(LoadedDocument document) {
        if (document.getContent() == null || document.getContent().isEmpty()) {
            log.warn("Document {} has no content to split", document.getId());
            return List.of();
        }
        
        // First, split into sentences
        List<String> sentences = splitIntoSentences(document.getContent());
        
        if (sentences.isEmpty()) {
            return List.of();
        }
        
        log.debug("Document {} split into {} sentences", document.getId(), sentences.size());
        
        // Group sentences into semantically coherent chunks
        List<List<String>> chunks = groupSentencesSemantically(sentences);
        
        log.debug("Grouped sentences into {} semantic chunks", chunks.size());
        
        // Convert to Document objects
        List<Document> documents = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            List<String> sentenceGroup = chunks.get(i);
            String chunkText = String.join(" ", sentenceGroup);
            String chunkId = document.getId() + "-semantic-" + i;
            
            // Create metadata for chunk
            Map<String, Object> metadata = new HashMap<>();
            
            // Preserve original document metadata if requested
            if (preserveMetadata && document.getMetadata() != null) {
                metadata.putAll(document.getMetadata());
            }
            
            // Add chunk-specific metadata
            if (addChunkMetadata) {
                metadata.put("sourceDocumentId", document.getId());
                metadata.put("sourceDocumentSource", document.getSource());
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", chunks.size());
                metadata.put("chunkSize", chunkText.length());
                metadata.put("sentenceCount", sentenceGroup.size());
                metadata.put("splitterType", "semantic");
                metadata.put("splitterTargetSize", targetChunkSize);
                metadata.put("splitterSimilarityThreshold", similarityThreshold);
            }
            
            Document doc = new Document(
                chunkId,
                null, // No embedding yet
                chunkText,
                metadata
            );
            
            documents.add(doc);
        }
        
        return documents;
    }
    
    /**
     * Split text into sentences.
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator sentenceIterator = BreakIterator.getSentenceInstance(Locale.getDefault());
        sentenceIterator.setText(text);
        
        int start = sentenceIterator.first();
        int end = sentenceIterator.next();
        
        while (end != BreakIterator.DONE) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            start = end;
            end = sentenceIterator.next();
        }
        
        return sentences;
    }
    
    /**
     * Group sentences into semantically coherent chunks.
     */
    private List<List<String>> groupSentencesSemantically(List<String> sentences) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> currentChunk = new ArrayList<>();
        int currentSize = 0;
        
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int sentenceSize = sentence.length();
            
            // If adding this sentence would exceed max size, finalize current chunk
            if (!currentChunk.isEmpty() && currentSize + sentenceSize > maxChunkSize) {
                chunks.add(new ArrayList<>(currentChunk));
                currentChunk.clear();
                currentSize = 0;
            }
            
            // Add sentence to current chunk
            currentChunk.add(sentence);
            currentSize += sentenceSize;
            
            // Check if we should start a new chunk based on size or semantic boundary
            if (currentSize >= targetChunkSize) {
                // Check semantic similarity with next sentence
                if (i < sentences.size() - 1) {
                    String nextSentence = sentences.get(i + 1);
                    float similarity = calculateSimilarity(
                        String.join(" ", currentChunk), 
                        nextSentence
                    );
                    
                    // If similarity is low, this is a good breaking point
                    if (similarity < similarityThreshold) {
                        chunks.add(new ArrayList<>(currentChunk));
                        currentChunk.clear();
                        currentSize = 0;
                    }
                } else {
                    // Last sentence, finalize chunk
                    chunks.add(new ArrayList<>(currentChunk));
                    currentChunk.clear();
                    currentSize = 0;
                }
            }
        }
        
        // Add any remaining sentences
        if (!currentChunk.isEmpty()) {
            // If the last chunk is too small, merge with previous
            if (chunks.size() > 0 && currentSize < minChunkSize) {
                List<String> lastChunk = chunks.get(chunks.size() - 1);
                lastChunk.addAll(currentChunk);
            } else {
                chunks.add(currentChunk);
            }
        }
        
        return chunks;
    }
    
    /**
     * Calculate semantic similarity between two texts.
     */
    private float calculateSimilarity(String text1, String text2) {
        try {
            // Generate embeddings
            Response<Embedding> response1 = embeddingModel.embed(TextSegment.from(text1));
            Response<Embedding> response2 = embeddingModel.embed(TextSegment.from(text2));
            
            float[] vector1 = response1.content().vector();
            float[] vector2 = response2.content().vector();
            
            // Calculate cosine similarity
            return cosineSimilarity(vector1, vector2);
            
        } catch (Exception e) {
            log.warn("Failed to calculate similarity, using default threshold", e);
            return similarityThreshold; // Default to threshold to avoid breaking
        }
    }
    
    /**
     * Calculate cosine similarity between two vectors.
     */
    private float cosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }
        
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}