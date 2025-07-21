package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramSearchHit {
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("start")
    private Double start;
    
    @JsonProperty("end")
    private Double end;
    
    @JsonProperty("snippet")
    private String snippet;
}