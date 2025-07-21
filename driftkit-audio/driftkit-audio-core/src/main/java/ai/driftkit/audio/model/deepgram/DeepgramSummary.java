package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramSummary {
    
    @JsonProperty("summary")
    private String summary;
    
    @JsonProperty("start")
    private Double start;
    
    @JsonProperty("end")
    private Double end;
}