package ai.driftkit.embedding.core.cohere;// CohereEmbeddingRequest.java
import lombok.Data;
import java.util.List;

@Data
public class CohereEmbeddingRequest {

    private List<String> texts;
}