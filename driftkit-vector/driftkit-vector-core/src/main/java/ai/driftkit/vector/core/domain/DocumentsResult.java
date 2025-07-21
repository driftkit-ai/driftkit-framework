package ai.driftkit.vector.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DocumentsResult class revised for serialization compatibility
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentsResult {
    public static final DocumentsResult EMPTY = new DocumentsResult();

    private List<ResultEntry> result = new ArrayList<>();

    public DocumentsResult(Map<Document, Float> docs) {
        docs.entrySet().forEach(e -> {
            result.add(new ResultEntry(e.getKey(), e.getValue()));
        });
    }

    @JsonIgnore
    public Document first() {
        return result.isEmpty() ? null : result.get(0).getDocument();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return result == null || result.isEmpty();
    }

    @JsonIgnore
    public void put(Document doc, Float value) {
        result.add(new ResultEntry(doc, value));
    }

    @JsonIgnore
    public List<Document> documents() {
        List<Document> docs = new ArrayList<>();
        for (ResultEntry entry : result) {
            docs.add(entry.getDocument());
        }
        return docs;
    }

    @JsonIgnore
    public int size() {
        return isEmpty() ? 0 : result.size();
    }

    /**
     * ResultEntry class to hold Document and its associated Float value
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultEntry {
        private Document document;
        private Float value;
    }
}