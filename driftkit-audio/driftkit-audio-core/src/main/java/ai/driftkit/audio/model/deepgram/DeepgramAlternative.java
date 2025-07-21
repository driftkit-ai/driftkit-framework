package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DeepgramAlternative {
    
    @JsonProperty("transcript")
    private String transcript;
    
    @JsonProperty("confidence")
    private Double confidence;
    
    @JsonProperty("words")
    private List<DeepgramWord> words;
    
    @JsonProperty("paragraphs")
    private DeepgramParagraphs paragraphs;
    
    @JsonProperty("entities")
    private List<DeepgramEntity> entities;
    
    @JsonProperty("translations")
    private List<DeepgramTranslation> translations;
    
    @JsonProperty("summaries")
    private List<DeepgramSummary> summaries;
    
    @JsonProperty("languages")
    private List<String> languages;
}