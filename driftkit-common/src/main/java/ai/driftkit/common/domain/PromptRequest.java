package ai.driftkit.common.domain;

import ai.driftkit.common.domain.client.ResponseFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromptRequest {
    private String chatId;
    private List<PromptIdRequest> promptIds;
    private Map<String, Object> variables;
    private String workflow;
    private String modelId;
    private String checkerPrompt;
    private Language language;
    private boolean savePrompt;
    private Boolean logprobs;
    private Integer topLogprobs;
    private String purpose;
    private List<String> imageBase64;
    private String imageMimeType;
    private Boolean jsonResponse;
    private ResponseFormat responseFormat;

    public PromptRequest(List<String> promptIds, String chatId, Map<String, Object> variables, Language language) {
        this(promptIds, null, "reasoning-lite", null, chatId, variables, language, null, true, null);
    }

    public PromptRequest(PromptIdRequest idRequest, String chatId, Map<String, Object> variables, Language language) {
        this(null, List.of(idRequest), "reasoning-lite", null, chatId, variables, language, null, true, null);
    }
    
    public PromptRequest(List<String> promptIds, String chatId, Map<String, Object> variables, Language language, String purpose) {
        this(promptIds, null, "reasoning-lite", null, chatId, variables, language, purpose, true, null);
    }

    public PromptRequest(PromptIdRequest idRequest, String chatId, Map<String, Object> variables, Language language, String purpose) {
        this(null, List.of(idRequest), "reasoning-lite", null, chatId, variables, language, purpose, true, null);
    }

    @Builder
    public PromptRequest(List<String> promptIds, List<PromptIdRequest> idRequests, String workflow, String modelId, String chatId, Map<String, Object> variables, Language language, String purpose, Boolean jsonResponse, ResponseFormat responseFormat) {
        this.workflow = workflow;
        this.modelId = modelId;
        this.chatId = chatId;
        this.jsonResponse = jsonResponse;
        this.responseFormat = responseFormat;
        this.promptIds = idRequests == null ? promptIds.stream()
                .map(promptId -> new PromptIdRequest(promptId, null, null))
                .collect(Collectors.toList()) : idRequests;
        this.variables = variables;
        this.language = language;
        this.purpose = purpose;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptIdRequest {
        private String promptId;
        private String prompt;
        private Double temperature;
    }
}