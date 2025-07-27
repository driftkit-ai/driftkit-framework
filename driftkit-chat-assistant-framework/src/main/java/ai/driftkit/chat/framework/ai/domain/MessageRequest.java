package ai.driftkit.chat.framework.ai.domain;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.client.ResponseFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageRequest {
    private String message;
    private Language language;
    private String chatId;
    private String workflow;
    private Boolean jsonResponse;
    private String systemMessage;
    private Map<String, String> variables;
    private Boolean logprobs;
    private Integer topLogprobs;
    private String model;
    private Double temperature;
    private String purpose;
    private List<String> imageBase64;
    private String imageMimeType;
    private ResponseFormat responseFormat;
}