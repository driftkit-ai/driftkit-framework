package ai.driftkit.chat.framework.ai.domain;

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
    private Map<String, String> variables;
    private String modelId;
    private String workflow;
    private String language;
    private String purpose;
    private List<String> imageBase64;
    private String imageMimeType;

    public PromptRequest(List<String> promptIds, String chatId, Map<String, String> variables, MaterialLanguage language) {
        this(promptIds, null, "reasoning-lite", null, chatId, variables, language, null);
    }

    public PromptRequest(PromptIdRequest idRequest, String chatId, Map<String, String> variables, MaterialLanguage language) {
        this(null, List.of(idRequest), "reasoning-lite", null, chatId, variables, language, null);
    }
    
    public PromptRequest(List<String> promptIds, String chatId, Map<String, String> variables, MaterialLanguage language, String purpose) {
        this(promptIds, null, "reasoning-lite", null, chatId, variables, language, purpose);
    }

    public PromptRequest(PromptIdRequest idRequest, String chatId, Map<String, String> variables, MaterialLanguage language, String purpose) {
        this(null, List.of(idRequest), "reasoning-lite", null, chatId, variables, language, purpose);
    }

    @Builder
    public PromptRequest(List<String> promptIds, List<PromptIdRequest> idRequests, String workflow, String modelId, String chatId, Map<String, String> variables, MaterialLanguage language, String purpose) {
        this.workflow = workflow;
        this.modelId = modelId;
        this.chatId = chatId;
        this.promptIds = idRequests == null ? promptIds.stream()
                .map(promptId -> new PromptIdRequest(promptId, null, null))
                .collect(Collectors.toList()) : idRequests;
        this.variables = variables;
        this.language = language == null ? null : language.toString().toLowerCase();
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