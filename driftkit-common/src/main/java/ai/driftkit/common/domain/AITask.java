package ai.driftkit.common.domain;

import ai.driftkit.common.domain.client.ResponseFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class AITask {
//    @Id
    @NotNull
    private String messageId;

    @NotNull
    private String chatId;

    @NotNull
    private String message;

    private String systemMessage;

    private String gradeComment;

    @NotNull
    private Grade grade = Grade.FAIR;

    @NotNull
    private long createdTime;

    @NotNull
    private long responseTime;

    private String workflow;
    private String contextJson;

    private String workflowStopEvent;

    private Language language = Language.GENERAL;

    private boolean jsonRequest;
    private boolean jsonResponse;
    private ResponseFormat responseFormat;
    private String modelId;
    private List<String> promptIds;
    private Map<String, Object> variables;
    private Boolean logprobs;
    private Integer topLogprobs;
    private String purpose;
    private List<String> imageBase64;
    private String imageMimeType;


}