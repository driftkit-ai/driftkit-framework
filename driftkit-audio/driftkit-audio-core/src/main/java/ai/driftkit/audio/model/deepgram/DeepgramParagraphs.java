package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramParagraphs {
    
    @JsonProperty("transcript")
    private String transcript;
    
    @JsonProperty("paragraphs")
    private DeepgramParagraph[] paragraphs;
}