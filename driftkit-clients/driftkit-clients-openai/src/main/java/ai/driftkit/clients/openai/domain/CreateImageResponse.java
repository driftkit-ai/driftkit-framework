package ai.driftkit.clients.openai.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateImageResponse {

    // Unix timestamp of image creation
    private Long created;

    private List<ImageData> data;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageData {

        // URL of the generated image if response_format is "url"
        private String url;

        // Base64-encoded image if response_format is "b64_json"
        @JsonProperty("b64_json")
        private String b64Json;

        // Revised prompt used for image generation, if available
        @JsonProperty("revised_prompt")
        private String revisedPrompt;
    }
}