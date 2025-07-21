package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DeepgramResponse {
    
    @JsonProperty("metadata")
    private DeepgramMetadata metadata;
    
    @JsonProperty("results")
    private DeepgramResults results;
    
    @JsonProperty("language")
    private String language;
    
    @JsonProperty("is_final")
    private Boolean isFinal;
    
    @JsonProperty("speech_final")
    private Boolean speechFinal;
    
    @JsonProperty("channel_index")
    private List<Integer> channelIndex;
    
    @JsonProperty("duration")
    private Double duration;
    
    @JsonProperty("start")
    private Double start;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("channel")
    private DeepgramChannel channel;
    
    @JsonProperty("from_finalize")
    private Boolean fromFinalize;
    
    public Map<String, Object> toMap() {
        return Map.of(
            "metadata", metadata,
            "results", results == null ? "" : results,
            "language", language != null ? language : "en",
            "is_final", isFinal != null ? isFinal : false,
            "speech_final", speechFinal != null ? speechFinal : false,
            "channel_index", channelIndex,
            "duration", duration,
            "start", start,
            "type", type
        );
    }
}