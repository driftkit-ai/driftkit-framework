package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramEntity {
    
    @JsonProperty("label")
    private String label;
    
    @JsonProperty("value")
    private String value;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("start")
    private Double start;
    
    @JsonProperty("end")
    private Double end;
}