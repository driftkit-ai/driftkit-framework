package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramSentence {
    
    @JsonProperty("text")
    private String text;
    
    @JsonProperty("start")
    private Double start;
    
    @JsonProperty("end")
    private Double end;
}