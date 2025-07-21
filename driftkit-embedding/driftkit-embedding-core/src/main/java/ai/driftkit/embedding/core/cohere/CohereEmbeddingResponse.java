package ai.driftkit.embedding.core.cohere;// CohereEmbeddingResponse.java
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
public class CohereEmbeddingResponse {

    private List<List<Double>> embeddings;

    private String model;

    private String apiVersion;
}