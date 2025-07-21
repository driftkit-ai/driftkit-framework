package ai.driftkit.vector.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    private String id;
    private float[] vector;
    private String pageContent;
    private Map<String, Object> metadata;

    public Document(String id, float[] vector, String content) {
        this(id, vector, content,null);
    }
}
