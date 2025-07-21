package ai.driftkit.embedding.core.domain;// Usage.java
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class Usage {

    @JsonProperty("prompt_tokens")
    private int promptTokens;

    @JsonProperty("total_tokens")
    private int totalTokens;
}