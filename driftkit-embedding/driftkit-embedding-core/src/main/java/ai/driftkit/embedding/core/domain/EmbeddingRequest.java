package ai.driftkit.embedding.core.domain;// EmbeddingRequest.java
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
public class EmbeddingRequest {

    private String model;

    private List<String> input;
}