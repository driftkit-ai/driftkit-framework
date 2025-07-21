package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramWord {
    
    @JsonProperty("word")
    private String word;
    
    @JsonProperty("start")
    private Double start;
    
    @JsonProperty("end")
    private Double end;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("punctuated_word")
    private String punctuatedWord;
    
    @JsonProperty("speaker")
    private Integer speaker;
    
    @JsonProperty("language")
    private String language;
}