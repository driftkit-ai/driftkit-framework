package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DeepgramResults {
    
    @JsonProperty("channels")
    private List<DeepgramChannel> channels;
    
    @JsonProperty("utterances")
    private List<DeepgramUtterance> utterances;
}