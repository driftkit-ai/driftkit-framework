package ai.driftkit.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LLMRequest {
    private String chatId;
    @NotNull
    private String message;
    private List<String> promptIds;
    private String systemMessage;
    private String workflow;
    private boolean jsonResponse;
    private Language language;
    private Map<String, Object> variables;
    private Boolean logprobs;
    private Integer topLogprobs;
    private String purpose;
    private String model;
    private List<String> imagesBase64;
    private String imageMimeType;
}