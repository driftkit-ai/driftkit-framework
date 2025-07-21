package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DeepgramParagraph {
    
    @JsonProperty("sentences")
    private List<DeepgramSentence> sentences;
    
    @JsonProperty("start")
    private Double start;
    
    @JsonProperty("end")
    private Double end;
    
    @JsonProperty("num_words")
    private Integer numWords;
    
    @JsonProperty("speaker")
    private Integer speaker;
}