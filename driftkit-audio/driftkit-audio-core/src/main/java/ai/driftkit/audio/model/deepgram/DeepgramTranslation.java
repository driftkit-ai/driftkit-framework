package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramTranslation {
    
    @JsonProperty("language")
    private String language;
    
    @JsonProperty("translation")
    private String translation;
}