package ai.driftkit.embedding.core.domain;// EmbeddingResponse.java
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
public class EmbeddingResponse {

    private String object;

    private List<EmbeddingData> data;

    private Usage usage;
}