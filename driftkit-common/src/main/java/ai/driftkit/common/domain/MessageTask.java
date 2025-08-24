package ai.driftkit.common.domain;

import ai.driftkit.common.domain.client.LogProbs;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.common.domain.client.ResponseFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageTask extends AITask {
    protected String result;
    protected String imageTaskId;
    protected Double temperature;
    protected LogProbs logProbs;
    
    @Builder
    public MessageTask(
            String messageId,
            String chatId,
            String message,
            String systemMessage,
            String gradeComment,
            Grade grade,
            long createdTime,
            long responseTime,
            String modelId,
            String result,
            String imageTaskId,
            List<String> promptIds,
            Double temperature,
            String workflow,
            String context,
            Language language,
            Map<String, Object> variables,
            boolean jsonRequest,
            boolean jsonResponse,
            ResponseFormat responseFormat,
            String workflowStopEvent,
            Boolean logprobs,
            Integer topLogprobs,
            LogProbs logProbs,
            String purpose,
            List<String> imageBase64,
            String imageMimeType
    ) {
        super(messageId, chatId, message, systemMessage, gradeComment, grade, createdTime, responseTime, workflow, context, workflowStopEvent, language, jsonRequest, jsonResponse, responseFormat, modelId, promptIds, variables, logprobs, topLogprobs, purpose, imageBase64, imageMimeType);
        this.result = result;
        this.imageTaskId = imageTaskId;
        this.temperature = temperature;
        this.logProbs = logProbs;
    }

    /**
     * Extract and save token logprobs from a model response if available.
     *
     * @param response The model response potentially containing logprobs
     */
    public void updateWithResponseLogprobs(ModelTextResponse response) {
        if (Boolean.TRUE.equals(this.getLogprobs()) &&
                response != null && !response.getChoices().isEmpty() &&
                response.getChoices().get(0).getLogprobs() != null) {
            if (logProbs == null) {
                this.logProbs = new LogProbs(new ArrayList<>());
            }
            this.logProbs.getContent().addAll(response.getChoices().get(0).getLogprobs().getContent());
        }
    }

    /**
     * Extract logprobs parameters from a potential MessageTask object.
     *
     * @param taskObj an object that might be a MessageTask
     * @return LogProbsParams object containing logprobs parameters
     */
    public static LogProbsParams extractLogProbsParams(Object taskObj) {
        LogProbsParams params = new LogProbsParams();
        
        if (taskObj instanceof MessageTask task) {
            params.setLogprobs(task.getLogprobs());
            params.setTopLogprobs(task.getTopLogprobs());
        }
        
        return params;
    }


    /**
     * Contains parameters for token logprobs functionality
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LogProbsParams {
        private Boolean logprobs;
        private Integer topLogprobs;
    }
}