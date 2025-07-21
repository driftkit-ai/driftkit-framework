package ai.driftkit.context.core.domain;

import ai.driftkit.common.domain.Language;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request object for prompt execution containing all necessary parameters
 * for processing prompts in workflows.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    /**
     * Individual prompt identifier request with optional temperature setting
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptIdRequest {
        private String promptId;
        private String prompt;
        private Double temperature;
    }
}