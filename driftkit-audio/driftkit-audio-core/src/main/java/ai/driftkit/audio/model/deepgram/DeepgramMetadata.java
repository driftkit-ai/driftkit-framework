package ai.driftkit.audio.model.deepgram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DeepgramMetadata {
    
    @JsonProperty("transaction_key")
    private String transactionKey;
    
    @JsonProperty("request_id")
    private String requestId;
    
    @JsonProperty("sha256")
    private String sha256;
    
    @JsonProperty("created")
    private String created;
    
    @JsonProperty("duration")
    private Double duration;
    
    @JsonProperty("channels")
    private Integer channels;
    
    @JsonProperty("models")
    private String[] models;
    
    @JsonProperty("model_info")
    private Object modelInfo;
    
    @JsonProperty("model_uuid")
    private String modelUuid;
}