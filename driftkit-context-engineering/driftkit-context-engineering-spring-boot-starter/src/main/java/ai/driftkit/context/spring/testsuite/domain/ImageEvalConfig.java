package ai.driftkit.context.spring.testsuite.domain;

import ai.driftkit.common.domain.ImageMessageTask;
import ai.driftkit.context.spring.testsuite.domain.EvaluationResult.EvaluationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.util.StringUtils;

/**
 * Configuration for evaluating image generation
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageEvalConfig extends EvaluationConfig {

    /**
     * The criteria to evaluate (what aspect of the image to check)
     */
    private ImageEvaluationCriteria criteria;
    
    /**
     * Expected number of images to be generated
     */
    private Integer expectedImageCount;
    
    /**
     * Expected minimum file size in bytes
     */
    private Long minImageSize;
    
    /**
     * Expected maximum file size in bytes
     */
    private Long maxImageSize;
    
    /**
     * Expected image aspect ratio (width/height, e.g. 1.0 for square)
     * Allows for some tolerance (+/- 0.1)
     */
    private Double expectedAspectRatio;
    
    /**
     * Whether to check that revised prompt is provided
     */
    private Boolean hasRevisedPrompt;
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        // For image evaluation, we need the ImageMessageTask from additionalContext
        if (context.getAdditionalContext() == null || !(context.getAdditionalContext() instanceof ImageMessageTask)) {
            return EvaluationResult.EvaluationOutput.builder()
                    .status(EvaluationResult.EvaluationStatus.ERROR)
                    .message("Image task not available in evaluation context")
                    .build();
        }
        
        ImageMessageTask imageTask = (ImageMessageTask) context.getAdditionalContext();
        
        if (imageTask.getImages() == null || imageTask.getImages().isEmpty()) {
            return EvaluationResult.EvaluationOutput.builder()
                    .status(EvaluationResult.EvaluationStatus.FAILED)
                    .message("No images generated")
                    .build();
        }
        
        // Evaluate based on the specified criteria
        switch (criteria) {
            case IMAGE_COUNT:
                return evaluateImageCount(imageTask);
            case IMAGE_SIZE:
                return evaluateImageSize(imageTask);
            case ASPECT_RATIO:
                return evaluateAspectRatio(imageTask);
            case REVISED_PROMPT:
                return evaluateRevisedPrompt(imageTask);
            case BASIC_VALIDATION:
            default:
                return evaluateBasicValidation(imageTask);
        }
    }
    
    private EvaluationResult.EvaluationOutput evaluateImageCount(ImageMessageTask imageTask) {
        int actualCount = imageTask.getImages().size();
        
        if (expectedImageCount != null && actualCount != expectedImageCount) {
            return applyNegation(EvaluationResult.EvaluationOutput.builder()
                    .status(EvaluationResult.EvaluationStatus.FAILED)
                    .message("Expected " + expectedImageCount + " images but got " + actualCount)
                    .build());
        }
        
        return applyNegation(EvaluationResult.EvaluationOutput.builder()
                .status(EvaluationResult.EvaluationStatus.PASSED)
                .message("Image count: " + actualCount)
                .build());
    }
    
    private EvaluationResult.EvaluationOutput evaluateImageSize(ImageMessageTask imageTask) {
        boolean allValid = true;
        StringBuilder message = new StringBuilder("Image sizes: ");
        
        for (int i = 0; i < imageTask.getImages().size(); i++) {
            ImageMessageTask.GeneratedImage img = imageTask.getImages().get(i);
            if (img.getData() == null) {
                message.append("Image #").append(i + 1).append(" has no data. ");
                allValid = false;
                continue;
            }
            
            long size = img.getData().length;
            message.append(size).append(" bytes");
            
            if (minImageSize != null && size < minImageSize) {
                message.append(" (too small, min: ").append(minImageSize).append(")");
                allValid = false;
            }
            
            if (maxImageSize != null && size > maxImageSize) {
                message.append(" (too large, max: ").append(maxImageSize).append(")");
                allValid = false;
            }
            
            if (i < imageTask.getImages().size() - 1) {
                message.append(", ");
            }
        }
        
        return applyNegation(EvaluationResult.EvaluationOutput.builder()
                .status(allValid ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(message.toString())
                .build());
    }
    
    private EvaluationResult.EvaluationOutput evaluateAspectRatio(ImageMessageTask imageTask) {
        // Note: This would require image metadata extraction, which isn't available in the current code
        // A placeholder implementation that would need to be enhanced with actual image dimension reading
        return applyNegation(EvaluationResult.EvaluationOutput.builder()
                .status(EvaluationStatus.ERROR)
                .message("Aspect ratio evaluation requires image dimension extraction, which is not implemented")
                .build());
    }
    
    private EvaluationResult.EvaluationOutput evaluateRevisedPrompt(ImageMessageTask imageTask) {
        boolean allHaveRevised = true;
        StringBuilder message = new StringBuilder("Revised prompts: ");
        
        for (int i = 0; i < imageTask.getImages().size(); i++) {
            ImageMessageTask.GeneratedImage img = imageTask.getImages().get(i);
            boolean hasRevised = StringUtils.hasText(img.getRevisedPrompt());
            
            if (!hasRevised) {
                allHaveRevised = false;
                message.append("Image #").append(i + 1).append(" has no revised prompt. ");
            } else {
                message.append("Image #").append(i + 1).append(" has revised prompt. ");
            }
        }
        
        boolean passed = hasRevisedPrompt == null || allHaveRevised == hasRevisedPrompt;
        
        return applyNegation(EvaluationResult.EvaluationOutput.builder()
                .status(passed ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(message.toString())
                .build());
    }
    
    private EvaluationResult.EvaluationOutput evaluateBasicValidation(ImageMessageTask imageTask) {
        boolean valid = true;
        StringBuilder message = new StringBuilder("Image validation: ");
        
        // Check that all images have data or URLs
        for (int i = 0; i < imageTask.getImages().size(); i++) {
            ImageMessageTask.GeneratedImage img = imageTask.getImages().get(i);
            boolean hasData = img.getData() != null && img.getData().length > 0;
            boolean hasUrl = StringUtils.hasText(img.getUrl());
            
            if (!hasData && !hasUrl) {
                valid = false;
                message.append("Image #").append(i + 1).append(" has no data or URL. ");
            }
        }
        
        if (valid) {
            message.append("All images have valid data or URLs.");
        }
        
        return applyNegation(EvaluationResult.EvaluationOutput.builder()
                .status(valid ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                .message(message.toString())
                .build());
    }
    
    public enum ImageEvaluationCriteria {
        BASIC_VALIDATION,  // Check that images exist and have basic data
        IMAGE_COUNT,       // Check number of images generated
        IMAGE_SIZE,        // Check image file size
        ASPECT_RATIO,      // Check image dimensions/ratio
        REVISED_PROMPT     // Check if revised prompts are provided
    }
}