package ai.driftkit.clients.openai.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateImageRequest {

    // Model to use, e.g., "dall-e-3"
    @JsonProperty("model")
    private String model;

    @JsonProperty("prompt")
    private String prompt;

    // Number of images to generate, default is 1
    @JsonProperty("n")
    private Integer n = 1;

    // Size of the image, e.g., "1024x1024"
    @JsonProperty("size")
    private String size = "1024x1024";

    @JsonProperty("output_compression")
    private Integer compression;

    @JsonProperty("output_format")
    private String outputFormat;

    @JsonProperty("response_format")
    private ImageResponseFormat responseFormat = ImageResponseFormat.url;

    // Optional, style of the image
    @JsonProperty("style")
    private ImageStyle style = ImageStyle.vivid;

    // Optional, unique identifier for the end-user
    @JsonProperty("user")
    private String user;

    private Quality quality;

    public enum Quality {
        standard, hd, low, high, medium
    }

    public enum ImageResponseFormat {
        url, b64_json
    }

    public enum ImageStyle {
        vivid,
        natural
    }
}