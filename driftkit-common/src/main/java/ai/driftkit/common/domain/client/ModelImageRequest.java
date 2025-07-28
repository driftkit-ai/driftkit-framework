package ai.driftkit.common.domain.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelImageRequest {

    // Model to use, e.g., "dall-e-3"
    private String model;

    private String prompt;

    // Number of images to generate, default is 1
    private Integer n = 1;

    // Size of the image, e.g., "1024x1024"
    private String size = "1024x1024";

    // Quality as string to support different models with different quality enums
    private String quality;

    public enum Quality {
        standard, hd, low, high, medium;
    }
}