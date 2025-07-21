package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DeepgramChannel {
    
    @JsonProperty("search")
    private List<DeepgramSearch> search;
    
    @JsonProperty("alternatives")
    private List<DeepgramAlternative> alternatives;
}