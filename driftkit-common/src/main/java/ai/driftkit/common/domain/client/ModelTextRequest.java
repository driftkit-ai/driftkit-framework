package ai.driftkit.common.domain.client;

import ai.driftkit.common.domain.client.ModelContentMessage.ModelContentElement.ImageData;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // Exclude null fields for cleaner serialization
public class ModelTextRequest {

    private List<ModelContentMessage> messages;

    private Double temperature;
    private String model;
    private ReasoningEffort reasoningEffort = ReasoningEffort.medium;

    // Whether to enable log probabilities for token generation
    private Boolean logprobs;
    
    // Number of most likely tokens to return at each step (1-20)
    private Integer topLogprobs;

    /*
    Setting to { "type": "json_schema", "json_schema": {...} } enables Structured Outputs which ensures the model
    will match your supplied JSON schema. Learn more in the Structured Outputs guide.

    Setting to { "type": "json_object" } enables JSON mode, which ensures the message the model generates is valid JSON.

    Important: when using JSON mode, you must also instruct the model to produce JSON yourself via a system or user message.
    Without this, the model may generate an unending stream of whitespace until the generation reaches the token limit,
    resulting in a long-running and seemingly "stuck" request. Also note that the message content may be partially cut
    off if finish_reason="length", which indicates the generation exceeded max_tokens or the conversation exceeded the max context length.
     */
    private ResponseFormat responseFormat;

    /*
    Controls which (if any) tool is called by the model.
    none means the model will not call any tool and instead generates a message.
    auto means the model can pick between generating a message or calling one or more tools.
     */
    private ToolMode toolMode;
    
    /*
    A list of tools the model may call. Currently, only functions are supported as a tool.
    Use this to provide a list of functions the model may generate JSON inputs for.
     */
    private List<ModelClient.Tool> tools;

    private PredictionContentItem prediction;

    public static ModelTextRequestBuilder create(Role role, String model, String str) {
        return create(role, model, List.of(str), Collections.emptyList());
    }

    public static ModelTextRequestBuilder create(Role role, String model, String str, ImageData image) {
        return create(role, model, List.of(str), List.of(image));
    }

    public static ModelTextRequestBuilder create(Role role, String model, List<String> str, List<ImageData> images) {
        return ModelTextRequest.builder()
                .messages(List.of(ModelContentMessage.create(role, str, images)))
                .model(model);
    }

    public enum ToolMode {
        none,
        auto,
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionContentItem {
        private String text;
    }

    public enum ReasoningEffort {
        medium,
        high,
        low,
        none,
        dynamic
    }

    public enum MessageType {
        image,
        image_url,
        text
    }
}