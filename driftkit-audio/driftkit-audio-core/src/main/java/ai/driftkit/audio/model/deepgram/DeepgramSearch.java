package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramSearch {
    
    @JsonProperty("query")
    private String query;
    
    @JsonProperty("hits")
    private DeepgramSearchHit[] hits;
}