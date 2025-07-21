package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DeepgramUtterance {
    
    @JsonProperty("start")
    private Double start;
    
    @JsonProperty("end")
    private Double end;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("channel")
    private Integer channel;
    
    @JsonProperty("transcript")
    private String transcript;
    
    @JsonProperty("words")
    private List<DeepgramWord> words;
    
    @JsonProperty("speaker")
    private Integer speaker;
    
    @JsonProperty("id")
    private String id;
}