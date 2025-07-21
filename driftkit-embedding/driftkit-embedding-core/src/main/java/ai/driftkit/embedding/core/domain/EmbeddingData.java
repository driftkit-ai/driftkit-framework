package ai.driftkit.embedding.core.domain;// EmbeddingData.java
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@Data
public class EmbeddingData {

    private String object;

    private int index;

    private float[] embedding;
}