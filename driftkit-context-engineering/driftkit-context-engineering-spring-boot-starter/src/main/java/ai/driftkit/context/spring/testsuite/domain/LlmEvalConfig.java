package ai.driftkit.context.spring.testsuite.domain;

import ai.driftkit.common.domain.ImageMessageTask;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.common.domain.client.ModelImageResponse.ModelContentMessage;
import ai.driftkit.common.domain.client.ModelTextResponse;
import ai.driftkit.context.spring.testsuite.service.EvaluationService;
import ai.driftkit.workflows.spring.domain.ModelRequestTrace;
import ai.driftkit.workflows.spring.service.AIService;
import ai.driftkit.workflows.spring.service.ModelRequestContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Configuration for evaluations that use an LLM to evaluate responses
 */
@Slf4j
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmEvalConfig extends EvaluationConfig {
    /**
     * The evaluation prompt to use
     * Available placeholders:
     * {{task}} - The original task/prompt
     * {{actual}} - The actual model response
     * {{expected}} - The reference/expected result from TestSetItem 
     */
    private String evaluationPrompt;
    
    /**
     * Model to use for evaluation (if null, uses the default model)
     */
    private String modelId;
    
    /**
     * If true, returns detailed feedback in addition to pass/fail
     */
    private boolean generateFeedback;
    
    /**
     * Temperature setting for the LLM evaluation
     */
    private Double temperature;
    
    /**
     * Result of LLM evaluation
     */
    @Data
    @Builder
    public static class LlmEvalResult {
        private boolean passed;
        private String message;
        private String feedback;
        private String prompt;
    }
    
    @Override
    public EvaluationResult.EvaluationOutput evaluate(EvaluationContext context) {
        try {
            String task = context.getTestSetItem().getMessage();
            String actual = context.getActualResult();
            String expected = context.getOriginalResult();
            AIService aiService = context.getAiService();
            
            // Check if AI service is available
            if (aiService == null) {
                log.error("AIService not provided in evaluation context");
                return EvaluationResult.EvaluationOutput.builder()
                        .status(EvaluationResult.EvaluationStatus.ERROR)
                        .message("AIService not available for LLM evaluation")
                        .build();
            }
            
            // Handle image evaluation if this is an image test item and we have image context
            if (context.getTestSetItem().isImageTask() && context.getAdditionalContext() instanceof ImageMessageTask) {
                return evaluateImages(context, aiService);
            }
            
            // Standard text evaluation
            // Prepare the evaluation prompt
            String prompt = getEvaluationPrompt();
            prompt = prompt.replace("{{task}}", task)
                    .replace("{{actual}}", String.valueOf(actual))
                    .replace("{{expected}}", String.valueOf(expected));
            
            // Prepare the message task with all necessary properties
            MessageTask messageTask = new MessageTask();
            messageTask.setMessageId(UUID.randomUUID().toString());
            messageTask.setMessage(prompt);
            
            // Use original workflow from test set item
            String workflow = context.getTestSetItem().getWorkflow();
            if (workflow != null) {
                messageTask.setWorkflow(workflow);
            }
            
            // Copy system message from test set item
            String systemMessage = context.getTestSetItem().getSystemMessage();
            if (systemMessage != null) {
                messageTask.setSystemMessage(systemMessage);
            }
            
            // Copy variables from test set item
            messageTask.setVariables(context.getTestSetItem().getVariablesAsObjectMap());
            
            // Set language from test set item (use GENERAL as default if not specified)
            messageTask.setLanguage(Optional.ofNullable(context.getTestSetItem().getLanguage()).orElse(Language.GENERAL));
            
            // Set JSON request/response flags
            messageTask.setJsonRequest(context.getTestSetItem().isJsonRequest());
            messageTask.setJsonResponse(context.getTestSetItem().isJsonResponse());
            
            // Set purpose to indicate this request is from test pipeline
            messageTask.setPurpose(EvaluationService.QA_TEST_PIPELINE_PURPOSE);
            
            // Copy log probability settings if available
            if (context.getTestSetItem().getLogprobs() != null) {
                messageTask.setLogprobs(context.getTestSetItem().getLogprobs() > 0);
            }
            
            if (context.getTestSetItem().getTopLogprobs() != null) {
                messageTask.setTopLogprobs(context.getTestSetItem().getTopLogprobs());
            }
            
            // Set model parameters if specified in the evaluation config
            if (getModelId() != null) {
                messageTask.setModelId(getModelId());
            } else {
                messageTask.setModelId(context.getTestSetItem().getModel());
            }
            
            if (getTemperature() != null) {
                messageTask.setTemperature(getTemperature());
            } else {
                messageTask.setTemperature(context.getTestSetItem().getTemperature());
            }
            
            // Run the evaluation using AIService.chat to ensure workflow support
            MessageTask response = aiService.chat(messageTask);
            String responseText = response.getResult();
            
            // Parse the response to determine pass/fail
            // Assuming the LLM returns a response with PASS/FAIL at the beginning
            boolean passed = responseText.toLowerCase().contains("pass") && !responseText.toLowerCase().contains("fail");
            
            LlmEvalResult evalResult = LlmEvalResult.builder()
                    .passed(passed)
                    .message(passed ? "LLM evaluation passed" : "LLM evaluation failed")
                    .feedback(responseText)
                    .prompt(prompt)
                    .build();
                    
            // Create result output and apply negation if configured
            EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                    .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                    .message(evalResult.getMessage())
                    .details(evalResult)
                    .build();
            
            return applyNegation(output);
            
        } catch (Exception e) {
            log.error("Error in LLM evaluation: {}", e.getMessage(), e);
            return EvaluationResult.EvaluationOutput.builder()
                    .status(EvaluationResult.EvaluationStatus.ERROR)
                    .message("LLM evaluation error: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Evaluates image content using LLM to compare the prompt with the generated image
     * This method creates a multimodal request that sends both the image and the prompt to the LLM
     */
    private EvaluationResult.EvaluationOutput evaluateImages(EvaluationContext context, AIService aiService) {
        try {
            // Get the image task from the additional context
            ImageMessageTask imageTask = (ImageMessageTask) context.getAdditionalContext();
            if (imageTask.getImages() == null || imageTask.getImages().isEmpty()) {
                return EvaluationResult.EvaluationOutput.builder()
                        .status(EvaluationResult.EvaluationStatus.FAILED)
                        .message("No images available for evaluation")
                        .build();
            }
            
            // Prepare image data to send to the model
            ImageMessageTask.GeneratedImage generatedImage = imageTask.getImages().get(0);
            if (generatedImage.getData() == null || generatedImage.getData().length == 0) {
                return EvaluationResult.EvaluationOutput.builder()
                        .status(EvaluationResult.EvaluationStatus.FAILED)
                        .message("Image data not available for evaluation")
                        .build();
            }
            
            // Get the original prompt that was used to generate the image
            String imagePrompt = context.getTestSetItem().getMessage();
            
            // Create a specialized prompt for LLM to evaluate image-text matching
            String evaluationPrompt = getEvaluationPrompt();
            if (evaluationPrompt == null || evaluationPrompt.isEmpty()) {
                // If no custom evaluation prompt was provided, use a default one
                evaluationPrompt = 
                    "I'm going to show you an image that was generated based on the following prompt:\n\n" +
                    "\"{{task}}\"\n\n" +
                    "Please carefully analyze the image and evaluate if it matches what was requested in the prompt.\n" +
                    "Consider elements like subjects, style, composition, colors, and overall faithfulness to the prompt.\n\n" +
                    "Respond with either PASS if the image properly represents the prompt, or FAIL if it doesn't match.\n" +
                    "Then provide a detailed explanation of your assessment, discussing specific elements that match or don't match.";
            }
            
            // Replace placeholders in the evaluation prompt
            evaluationPrompt = evaluationPrompt.replace("{{task}}", imagePrompt);
            
            // Create message task for evaluation
            MessageTask messageTask = new MessageTask();
            messageTask.setMessageId(UUID.randomUUID().toString());
            
            // Configure model parameters
            String workflow = context.getTestSetItem().getWorkflow();
            String modelId = getModelId() != null ? getModelId() : context.getTestSetItem().getModel();
            Double temperature = getTemperature() != null ? getTemperature() : context.getTestSetItem().getTemperature();
            
            // Create a request context for sending through ModelRequestService
            ModelRequestContext requestContext = ModelRequestContext.builder()
                    .contextId(messageTask.getMessageId())
                    .contextType(ModelRequestTrace.ContextType.MESSAGE_TASK)
                    .requestType(ModelRequestTrace.RequestType.IMAGE_TO_TEXT)
                    .promptText(evaluationPrompt)
                    .messageTask(messageTask)
                    .imageData(List.of(new ModelContentMessage.ModelContentElement.ImageData(
                        generatedImage.getData(), 
                        generatedImage.getMimeType() != null ? generatedImage.getMimeType() : "image/png"
                    )))
                    .purpose("image_evaluation_test")
                    .model(modelId) // Ensure model ID is set here
                    .build();
            
            // Set system message requesting visual analysis capabilities
            messageTask.setSystemMessage(
                "You are a specialist in analyzing and evaluating images. " +
                "You have excellent visual perception and can accurately describe and assess images. " +
                "Focus on whether the image faithfully represents what was requested in the prompt. " +
                "Be specific in your assessment, referencing visual elements, subjects, composition, style, etc."
            );
            
            // Set parameters
            messageTask.setMessage(evaluationPrompt);
            messageTask.setWorkflow(workflow);
            messageTask.setModelId(modelId);
            messageTask.setTemperature(temperature);
            messageTask.setPurpose("image_evaluation_test");
            
            // Send the evaluation request to the AI service
            // Using direct call to ModelRequestService
            ModelTextResponse modelResponse = aiService.getModelRequestService().imageToText(
                aiService.getModelClient(), 
                requestContext
            );
            
            String responseText = modelResponse.getResponse();
            messageTask.setResult(responseText);
            messageTask.setResponseTime(System.currentTimeMillis());
            
            // Parse the response to determine pass/fail
            String lowerCaseResponse = responseText.toLowerCase();
            boolean passed = lowerCaseResponse.contains("pass") && !lowerCaseResponse.contains("fail");
            
            LlmEvalResult evalResult = LlmEvalResult.builder()
                    .passed(passed)
                    .message(passed ? "Image evaluation passed" : "Image evaluation failed")
                    .feedback(responseText)
                    .prompt(evaluationPrompt)
                    .build();
                    
            // Create result output and apply negation if configured
            EvaluationResult.EvaluationOutput output = EvaluationResult.EvaluationOutput.builder()
                    .status(evalResult.isPassed() ? EvaluationResult.EvaluationStatus.PASSED : EvaluationResult.EvaluationStatus.FAILED)
                    .message(evalResult.getMessage())
                    .details(evalResult)
                    .build();
            
            return applyNegation(output);
            
        } catch (Exception e) {
            log.error("Error in LLM image evaluation: {}", e.getMessage(), e);
            return EvaluationResult.EvaluationOutput.builder()
                    .status(EvaluationResult.EvaluationStatus.ERROR)
                    .message("LLM image evaluation error: " + e.getMessage())
                    .build();
        }
    }
}