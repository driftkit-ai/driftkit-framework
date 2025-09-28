package ai.driftkit.common.domain.client;

import ai.driftkit.common.domain.ModelTrace;
import ai.driftkit.common.utils.JsonUtils;
import ai.driftkit.common.utils.ModelUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTextResponse {
    private String id;
    //openai.object
    private String method;
    private Long createdTime;
    private String model;
    private List<ResponseMessage> choices;
    private Usage usage;
    private ModelTrace trace;

    public JsonNode getResponseJson() throws JsonProcessingException {
        String resp = getResponseJsonString();

        if (resp == null) {
            return null;
        }

        return ModelUtils.parseJsonMessage(resp);
    }

    public <T> T getResponseJson(Class<T> cls) throws JsonProcessingException {
        String resp = getResponseJsonString();

        if (resp == null) {
            return null;
        }

        return JsonUtils.fromJson(resp, cls);
    }

    public String getResponseJsonString() {
        String resp = getResponse();

        if (StringUtils.isBlank(resp)) {
            return null;
        }

        if (!JsonUtils.isValidJSON(resp)) {
            resp = JsonUtils.fixIncompleteJSON(resp);
        }

        return resp;
    }

    public String getResponse() {
        if (CollectionUtils.isEmpty(choices)) {
            return null;
        }

        if (choices.size() > 1) {
            throw new RuntimeException("Unexpected response messages number [%s]".formatted(choices.size()));
        }

        return choices.getFirst().getMessage().getContent();
    }

    @Data
    @Builder
    public static class ResponseMessage {
        private Integer index;
        private ModelMessage message;
        private String finishReason;
        private LogProbs logprobs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
    
}