package ai.driftkit.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified CreatePromptRequest used across all DriftKit modules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreatePromptRequest {
    private String method;
    private String message;
    private String systemMessage;
    private String workflow;
    @Builder.Default
    private boolean jsonResponse = false;
    @Builder.Default
    private Language language = Language.GENERAL;
}