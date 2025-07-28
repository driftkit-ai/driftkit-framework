package ai.driftkit.context.spring.testsuite.service;

import ai.driftkit.common.domain.ImageMessageTask;
import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.context.core.util.PromptUtils;
import ai.driftkit.context.spring.testsuite.domain.*;
import ai.driftkit.context.spring.testsuite.repository.*;
import ai.driftkit.workflows.spring.domain.ImageMessageTaskEntity;
import ai.driftkit.workflows.spring.repository.ImageTaskRepository;
import ai.driftkit.workflows.spring.service.ImageModelService;
import ai.driftkit.workflows.spring.service.AIService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Service for managing evaluations for test sets
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final String QA_TEST_PIPELINE_PURPOSE = "qa_test_pipeline";

    private ExecutorService testExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors())
    );

    private final EvaluationRepository evaluationRepository;
    private final EvaluationResultRepository resultRepository;
    private final EvaluationRunRepository runRepository;
    private final TestSetItemRepository testSetItemRepository;
    private final TestSetRepository testSetRepository;
    private final AIService aiService;
    private final ImageModelService imageModelService;
    private final ImageTaskRepository imageTaskRepository;

    /**
     * Create a new evaluation
     */
    public Evaluation createEvaluation(Evaluation evaluation) {
        evaluation.setCreatedAt(System.currentTimeMillis());
        evaluation.setUpdatedAt(System.currentTimeMillis());
        return evaluationRepository.save(evaluation);
    }
    
    /**
     * Copy an evaluation to another test set
     */
    public Evaluation copyEvaluation(String evaluationId, String targetTestSetId) {
        Optional<Evaluation> existingOpt = evaluationRepository.findById(evaluationId);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Evaluation not found: " + evaluationId);
        }
        
        Evaluation existing = existingOpt.get();
        
        Evaluation copy = new Evaluation();
        copy.setName(existing.getName() + " (Copy)");
        copy.setDescription(existing.getDescription());
        copy.setTestSetId(targetTestSetId);
        copy.setType(existing.getType());
        copy.setConfig(existing.getConfig());
        copy.setCreatedAt(System.currentTimeMillis());
        copy.setUpdatedAt(System.currentTimeMillis());
        
        return evaluationRepository.save(copy);
    }

    /**
     * Get all evaluations for a test set
     */
    public List<Evaluation> getEvaluationsForTestSet(String testSetId) {
        return evaluationRepository.findByTestSetId(testSetId);
    }
    
    /**
     * Get all global evaluations (not tied to a specific test set)
     */
    public List<Evaluation> getGlobalEvaluations() {
        return evaluationRepository.findByTestSetIdIsNull();
    }

    /**
     * Get a specific evaluation
     */
    public Optional<Evaluation> getEvaluation(String id) {
        return evaluationRepository.findById(id);
    }

    /**
     * Update an evaluation
     */
    public Evaluation updateEvaluation(String id, Evaluation evaluation) {
        Optional<Evaluation> existing = evaluationRepository.findById(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Evaluation not found: " + id);
        }
        
        evaluation.setId(id);
        evaluation.setCreatedAt(existing.get().getCreatedAt());
        evaluation.setUpdatedAt(System.currentTimeMillis());
        return evaluationRepository.save(evaluation);
    }

    /**
     * Delete an evaluation
     */
    public void deleteEvaluation(String id) {
        resultRepository.deleteByEvaluationId(id);
        evaluationRepository.deleteById(id);
    }

    /**
     * Create a new evaluation run
     */
    public EvaluationRun createEvaluationRun(EvaluationRun run) {
        if (StringUtils.isNotBlank(run.getModelId()) && StringUtils.isNotBlank(run.getWorkflow())) {
            throw new IllegalArgumentException("Cannot specify both modelId and workflow. Choose one execution method.");
        }
        
        run.setStatus(EvaluationRun.RunStatus.QUEUED);
        run.setStartedAt(System.currentTimeMillis());
        return runRepository.save(run);
    }

    /**
     * Get all runs for a test set
     */
    public List<EvaluationRun> getRunsForTestSet(String testSetId) {
        return runRepository.findByTestSetId(testSetId);
    }
    
    /**
     * Get all runs across all test sets
     */
    public List<EvaluationRun> getAllRuns() {
        return runRepository.findAll();
    }

    /**
     * Get a specific run
     */
    public Optional<EvaluationRun> getRun(String id) {
        return runRepository.findById(id);
    }

    /**
     * Delete a run and all its results
     */
    public void deleteRun(String id) {
        resultRepository.deleteByRunId(id);
        runRepository.deleteById(id);
    }

    /**
     * Get all results for a run
     */
    public List<EvaluationResult> getResultsForRun(String runId) {
        return resultRepository.findByRunId(runId);
    }

    /**
     * Create a new evaluation run for a test set and immediately execute it
     */
    public EvaluationRun createAndExecuteRun(String testSetId) {
        EvaluationRun run = EvaluationRun.builder()
                .testSetId(testSetId)
                .name("Run " + DATE_FORMAT.format(new Date()))
                .description("Automatic run")
                .status(EvaluationRun.RunStatus.QUEUED)
                .startedAt(System.currentTimeMillis())
                .build();
                
        EvaluationRun savedRun = runRepository.save(run);
        
        final String runId = savedRun.getId();
        testExecutor.submit(() -> {
            try {
                Optional<EvaluationRun> optionalRun = runRepository.findById(runId);
                if (optionalRun.isEmpty()) {
                    log.error("Run not found: {}", runId);
                    return;
                }
                
                EvaluationRun runToExecute = optionalRun.get();
                executeRun(runToExecute);
            } catch (Exception e) {
                log.error("Error in background execution of run {}: {}", runId, e.getMessage(), e);
            }
        });
        
        return savedRun;
    }

    /**
     * Create and execute runs for all test sets in a folder
     */
    public List<EvaluationRun> createAndExecuteRunsForFolder(String folderId, String modelId, String workflow) {
        return createAndExecuteRunsForFolder(folderId, modelId, workflow, false);
    }
    
    /**
     * Create and execute runs for all test sets in a folder
     */
    public List<EvaluationRun> createAndExecuteRunsForFolder(String folderId, String modelId, String workflow, boolean regenerateImages) {
        log.info("Creating and executing runs for all test sets in folder: {}", folderId);
        log.info("Options: modelId={}, workflow={}, regenerateImages={}", modelId, workflow, regenerateImages);
        
        List<TestSet> testSets = testSetRepository.findByFolderIdOrderByCreatedAtDesc(folderId);
        if (testSets.isEmpty()) {
            log.warn("No test sets found in folder: {}", folderId);
            return Collections.emptyList();
        }

        List<EvaluationRun> runs = new ArrayList<>();

        for (TestSet testSet : testSets) {
            EvaluationRun run = EvaluationRun.builder()
                    .testSetId(testSet.getId())
                    .name("Folder Run " + DATE_FORMAT.format(new Date()))
                    .description("Run as part of folder execution")
                    .status(EvaluationRun.RunStatus.QUEUED)
                    .startedAt(System.currentTimeMillis())
                    .regenerateImages(regenerateImages)
                    .build();
            
            if (StringUtils.isNotBlank(modelId)) {
                run.setModelId(modelId);
            }
            
            if (StringUtils.isNotBlank(workflow)) {
                run.setWorkflow(workflow);
            }

            runs.add(run);
        }

        for (EvaluationRun run : runs) {
            try {
                executeRun(run);
            } catch (Exception e) {
                log.error("Error in background execution of run {}: {}", run.getId(), e.getMessage(), e);
            }
        }

        return runs;
    }

    /**
     * Start the evaluation process
     */
    public void executeEvaluationRun(String runId) {
        Optional<EvaluationRun> optionalRun = runRepository.findById(runId);
        if (optionalRun.isEmpty()) {
            log.error("Run not found: {}", runId);
            return;
        }
        
        EvaluationRun run = optionalRun.get();
        executeRun(run);
    }

    private Future<EvaluationRun> executeRun(EvaluationRun run) {
        return testExecutor.submit(() -> {
            run.setStatus(EvaluationRun.RunStatus.RUNNING);
            runRepository.save(run);

            try {
                List<TestSetItem> items = testSetItemRepository.findByTestSetId(run.getTestSetId()).stream()
                        .map(TestSetItem.class::cast)
                        .toList();
                if (items.isEmpty()) {
                    log.warn("No items found for test set: {}", run.getTestSetId());
                    completeRunWithStatus(run, EvaluationRun.RunStatus.COMPLETED);
                    return run;
                }

                List<Evaluation> evaluations = evaluationRepository.findByTestSetId(run.getTestSetId());
                if (evaluations.isEmpty()) {
                    log.warn("No evaluations found for test set: {}", run.getTestSetId());
                    completeRunWithStatus(run, EvaluationRun.RunStatus.COMPLETED);
                    return run;
                }

                Map<String, Integer> statusCounts = new HashMap<>();
                for (TestSetItem item : items) {
                    String actualResult;
                    ProcessingResult processingResult = null;

                    if (item.isImageTask()) {
                        processingResult = processImageTask(item, run);
                    } else {
                        processingResult = processWithAlternativePrompt(item, run);
                    }
                    actualResult = processingResult.getModelResult();

                    for (Evaluation evaluation : evaluations) {
                        EvaluationResult result;
                        if (processingResult != null) {
                            result = evaluateResult(item, actualResult, evaluation, run.getId(), processingResult);
                        } else {
                            result = evaluateResult(item, actualResult, evaluation, run.getId());
                        }

                        String statusKey = result.getStatus().toString();
                        statusCounts.put(statusKey, statusCounts.getOrDefault(statusKey, 0) + 1);
                    }
                }

                run.setStatusCounts(statusCounts);

                int pendingCount = statusCounts.getOrDefault("PENDING", 0);
                
                if (pendingCount > 0) {
                    completeRunWithStatus(run, EvaluationRun.RunStatus.PENDING);
                    log.info("Run {} set to PENDING status due to {} pending manual evaluations", run.getId(), pendingCount);
                } else if (statusCounts.getOrDefault("FAILED", 0) > 0 || statusCounts.getOrDefault("ERROR", 0) > 0) {
                    completeRunWithStatus(run, EvaluationRun.RunStatus.FAILED);
                    log.info("Run {} completed with FAILED status due to failed tests", run.getId());
                } else {
                    completeRunWithStatus(run, EvaluationRun.RunStatus.COMPLETED);
                    log.info("Run {} completed with COMPLETED status, all tests passed", run.getId());
                }

            } catch (Exception e) {
                log.error("Error executing evaluation run: {}", e.getMessage(), e);
                completeRunWithStatus(run, EvaluationRun.RunStatus.FAILED);
            }

            return run;
        });
    }

    private void completeRunWithStatus(EvaluationRun run, EvaluationRun.RunStatus status) {
        run.setStatus(status);
        run.setCompletedAt(System.currentTimeMillis());
        runRepository.save(run);
    }
    
    /**
     * Process a test set item with alternative prompt and track execution metrics
     */
    private ProcessingResult processWithAlternativePrompt(TestSetItem item, EvaluationRun run) {
        ProcessingResult result = new ProcessingResult();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Processing test item with ID: {} for run: {}", item.getId(), run.getId());
            
            MessageTask messageTask = new MessageTask();
            messageTask.setMessageId(UUID.randomUUID().toString());
            log.debug("Created message task with ID: {}", messageTask.getMessageId());
            
            String prompt;
            if (run.getAlternativePromptTemplate() != null) {
                prompt = run.getAlternativePromptTemplate();
                log.debug("Using alternative prompt template: {}", prompt);
            } else {
                prompt = item.getMessage();
                log.debug("Using original message from test item: {}", prompt);
            }
            messageTask.setMessage(prompt);
            
            result.setOriginalPrompt(prompt);
            
            if (run.getAlternativePromptId() != null) {
                log.debug("Setting alternative prompt ID: {}", run.getAlternativePromptId());
                if (messageTask.getPromptIds() == null) {
                    messageTask.setPromptIds(new ArrayList<>());
                }
                messageTask.getPromptIds().add(run.getAlternativePromptId());
            }
            
            Map<String, Object> variables = item.getVariablesAsObjectMap();
            messageTask.setVariables(variables);
            result.setPromptVariables(variables);
            log.debug("Set {} variables from test item", variables != null ? variables.size() : 0);
            
            messageTask.setSystemMessage(item.getSystemMessage());
            
            Language language = Optional.ofNullable(item.getLanguage()).orElse(Language.GENERAL);
            messageTask.setLanguage(language);
            log.debug("Using language: {}", language);
            
            messageTask.setWorkflow(item.getWorkflowType());
            log.debug("Set workflow: {}", item.getWorkflow());
            
            messageTask.setJsonRequest(item.isJsonRequest());
            messageTask.setJsonResponse(item.isJsonResponse());
            
            messageTask.setPurpose(QA_TEST_PIPELINE_PURPOSE);
            
            if (item.getLogprobs() != null) {
                messageTask.setLogprobs(item.getLogprobs() > 0);
            }
            
            if (item.getTopLogprobs() != null) {
                messageTask.setTopLogprobs(item.getTopLogprobs());
            }

            if (run.getTemperature() != null) {
                messageTask.setTemperature(run.getTemperature());
                log.debug("Using temperature from run: {}", run.getTemperature());
            } else if (item.getTemperature() != null) {
                messageTask.setTemperature(item.getTemperature());
                log.debug("Using temperature from test item: {}", item.getTemperature());
            }
            
            if (messageTask.getWorkflow() == null && messageTask.getModelId() == null) {
                String modelId;
                if (run.getModelId() != null) {
                    modelId = run.getModelId();
                    log.debug("Using model ID from run: {}", modelId);
                } else {
                    modelId = item.getModel();
                    log.debug("Using model ID from test item: {}", modelId);
                }
                messageTask.setModelId(modelId);
            }
            
            log.info("Sending request to AI service with message task: {}", messageTask.getMessageId());
            MessageTask response = aiService.chat(messageTask);
            
            if (response == null) {
                throw new IllegalStateException("AI service returned null response");
            }
            
            String responseText = response.getResult();
            log.info("Received response from AI service: {}", responseText != null ? "success" : "null");
            
            if (responseText == null || responseText.trim().isEmpty()) {
                log.warn("Empty response received from AI service for message task: {}", messageTask.getMessageId());
                result.setSuccess(false);
                result.setModelResult("ERROR: Empty response from model");
                result.setErrorDetails("The model returned an empty response. Check model configuration and permissions.");
                return result;
            }
            
            result.setModelResult(responseText);
            result.setSuccess(true);
            
            return result;
        } catch (Exception e) {
            log.error("Error processing with alternative prompt for test item {}: {}", 
                      item.getId(), e.getMessage(), e);
            
            result.setSuccess(false);
            result.setModelResult("ERROR: " + e.getMessage());
            result.setErrorDetails(e.getMessage() + "\n" + getStackTraceAsString(e));
            
            return result;
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            result.setProcessingTimeMs(duration);
            log.info("Finished processing test item in {}ms", duration);
        }
    }
    
    /**
     * Process an image test set item
     */
    private ProcessingResult processImageTask(TestSetItem item, EvaluationRun run) {
        ProcessingResult result = new ProcessingResult();
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Processing image test item with ID: {} for run: {}", item.getId(), run.getId());
            
            if (StringUtils.isBlank(item.getOriginalImageTaskId())) {
                throw new IllegalArgumentException("No original image task ID found for test item: " + item.getId());
            }
            
            String imageTaskId = item.getOriginalImageTaskId();
            Optional<ImageMessageTaskEntity> originalImageTask = imageTaskRepository.findById(imageTaskId);
            
            if (originalImageTask.isEmpty()) {
                throw new IllegalArgumentException("Original image task not found: " + imageTaskId);
            }
            
            ImageMessageTask imageTask = originalImageTask.get();
            
            result.setOriginalPrompt(imageTask.getMessage());
            
            if (run.isRegenerateImages()) {
                log.info("Regenerating image for test item {} (run configuration has regenerateImages=true)", item.getId());
                
                MessageTask messageTask = new MessageTask();
                messageTask.setMessageId(UUID.randomUUID().toString());
                messageTask.setChatId(imageTask.getChatId());
                String alternativePromptTemplate = run.getAlternativePromptTemplate();

                if (StringUtils.isNotBlank(alternativePromptTemplate)) {
                    messageTask.setMessage(PromptUtils.applyVariables(alternativePromptTemplate, item.getVariablesAsObjectMap()));
                    imageTask.setMessage(messageTask.getMessage());
                } else {
                    messageTask.setPromptIds(imageTask.getPromptIds());
                }

                messageTask.setSystemMessage(imageTask.getSystemMessage());
                messageTask.setPurpose(QA_TEST_PIPELINE_PURPOSE);
                
                if (StringUtils.isNotBlank(run.getWorkflow())) {
                    messageTask.setWorkflow(run.getWorkflow());
                } else {
                    messageTask.setWorkflow(imageTask.getWorkflow());
                }
                
                int numImages = imageTask.getImages() != null ? imageTask.getImages().size() : 1;
                
                ImageMessageTask newImageTask = imageModelService.addTaskSync(
                    messageTask,
                    imageTask.getMessage(),
                    numImages
                );
                
                result.setSuccess(true);
                result.setModelResult(String.format("Regenerated %d image(s)", 
                        newImageTask.getImages() != null ? newImageTask.getImages().size() : 0));
                
                result.setAdditionalContext(newImageTask);
            } else {
                log.info("Using existing image for test item {}", item.getId());
                
                result.setSuccess(true);
                result.setModelResult(String.format("Using existing image(s) (%d)", 
                        imageTask.getImages() != null ? imageTask.getImages().size() : 0));
                
                result.setAdditionalContext(imageTask);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error processing image task for test item {}: {}", 
                    item.getId(), e.getMessage(), e);
            
            result.setSuccess(false);
            result.setModelResult("ERROR: " + e.getMessage());
            result.setErrorDetails(e.getMessage() + "\n" + getStackTraceAsString(e));
            
            return result;
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            result.setProcessingTimeMs(duration);
            log.info("Finished processing image test item in {}ms", duration);
        }
    }
    
    /**
     * Helper class to store processing result and metadata
     */
    @Data
    private static class ProcessingResult {
        private boolean success;
        private String modelResult;
        private String originalPrompt;
        private Object promptVariables;
        private Long processingTimeMs;
        private String errorDetails;
        private Object additionalContext;
    }
    
    /**
     * Helper method to convert stack trace to string
     */
    private String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Evaluate a result against an evaluation
     */
    private EvaluationResult evaluateResult(TestSetItem item, String actualResult, Evaluation evaluation, String runId) {
        return evaluateResult(item, actualResult, evaluation, runId, null);
    }
    
    private EvaluationResult evaluateResult(TestSetItem item, String actualResult, Evaluation evaluation, String runId, ProcessingResult processingResult) {
        try {
            EvaluationContext.EvaluationContextBuilder contextBuilder = EvaluationContext.builder()
                    .testSetItem(item)
                    .originalResult(item.getResult())
                    .actualResult(actualResult)
                    .aiService(aiService);
            
            if (processingResult != null && processingResult.getAdditionalContext() != null) {
                contextBuilder.additionalContext(processingResult.getAdditionalContext());
            }
            
            EvaluationContext context = contextBuilder.build();
            
            boolean isImageEvaluation = item.isImageTask() && evaluation.getConfig() instanceof ImageEvalConfig;
            
            EvaluationResult.EvaluationOutput output = evaluation.getConfig().evaluate(context);
            
            EvaluationResult.EvaluationResultBuilder resultBuilder = EvaluationResult.builder()
                    .evaluationId(evaluation.getId())
                    .testSetItemId(item.getId())
                    .runId(runId)
                    .status(output.getStatus())
                    .message(output.getMessage())
                    .details(output.getDetails())
                    .createdAt(System.currentTimeMillis());
            
            if (processingResult != null) {
                resultBuilder
                    .originalPrompt(processingResult.getOriginalPrompt())
                    .modelResult(processingResult.getModelResult())
                    .promptVariables(processingResult.getPromptVariables())
                    .processingTimeMs(processingResult.getProcessingTimeMs())
                    .errorDetails(processingResult.getErrorDetails());
                
                if (processingResult.getAdditionalContext() instanceof ImageMessageTask) {
                    ImageMessageTask imageTask = (ImageMessageTask) processingResult.getAdditionalContext();
                    Map<String, Object> details = new HashMap<>();
                    details.put("imageTaskId", imageTask.getMessageId());
                    
                    if (output.getDetails() != null) {
                        if (output.getDetails() instanceof Map) {
                            ((Map) details).putAll((Map) output.getDetails());
                        }
                    }
                    
                    resultBuilder.details(details);
                }
            } else if (output.getOriginalPrompt() != null) {
                resultBuilder
                    .originalPrompt(output.getOriginalPrompt())
                    .modelResult(output.getModelResult())
                    .promptVariables(output.getPromptVariables())
                    .processingTimeMs(output.getProcessingTimeMs())
                    .errorDetails(output.getErrorDetails());
            }
            
            return resultRepository.save(resultBuilder.build());
            
        } catch (Exception e) {
            log.error("Error evaluating result: {}", e.getMessage(), e);
            
            EvaluationResult.EvaluationResultBuilder resultBuilder = EvaluationResult.builder()
                    .evaluationId(evaluation.getId())
                    .testSetItemId(item.getId())
                    .runId(runId)
                    .status(EvaluationResult.EvaluationStatus.ERROR)
                    .message("Evaluation error: " + e.getMessage())
                    .errorDetails(getStackTraceAsString(e))
                    .createdAt(System.currentTimeMillis());
            
            if (processingResult != null) {
                resultBuilder
                    .originalPrompt(processingResult.getOriginalPrompt())
                    .modelResult(processingResult.getModelResult())
                    .promptVariables(processingResult.getPromptVariables())
                    .processingTimeMs(processingResult.getProcessingTimeMs());
            }
            
            return resultRepository.save(resultBuilder.build());
        }
    }
    
    /**
     * Update the status of a manual evaluation result
     */
    public EvaluationResult updateManualEvaluationStatus(String resultId, boolean passed, String feedback) {
        Optional<EvaluationResult> optionalResult = resultRepository.findById(resultId);
        if (optionalResult.isEmpty()) {
            throw new IllegalArgumentException("Evaluation result not found: " + resultId);
        }
        
        EvaluationResult result = optionalResult.get();
        
        if (result.getStatus() != EvaluationResult.EvaluationStatus.PENDING) {
            throw new IllegalArgumentException("Cannot update status for non-manual evaluation or evaluation that has already been reviewed");
        }
        
        result.setStatus(passed ? 
                EvaluationResult.EvaluationStatus.PASSED : 
                EvaluationResult.EvaluationStatus.FAILED);
        
        result.setMessage(feedback);
        
        EvaluationResult savedResult = resultRepository.save(result);
        
        checkAndUpdateRunStatus(result.getRunId());
        
        return savedResult;
    }
    
    /**
     * Check if all manual evaluations in a run have been completed, and update run status if needed
     */
    private void checkAndUpdateRunStatus(String runId) {
        Optional<EvaluationRun> optionalRun = runRepository.findById(runId);
        if (optionalRun.isEmpty()) {
            log.warn("Couldn't find run with ID {} when checking manual evaluations", runId);
            return;
        }
        
        EvaluationRun run = optionalRun.get();
        
        if (run.getStatus() != EvaluationRun.RunStatus.PENDING) {
            return;
        }
        
        List<EvaluationResult> allResults = resultRepository.findByRunId(runId);
        
        long pendingCount = allResults.stream()
                .filter(r -> r.getStatus() == EvaluationResult.EvaluationStatus.PENDING)
                .count();
        
        if (pendingCount == 0) {
            log.info("All manual evaluations completed for run {}, updating status", runId);
            
            long failedCount = allResults.stream()
                    .filter(r -> r.getStatus() == EvaluationResult.EvaluationStatus.FAILED 
                             || r.getStatus() == EvaluationResult.EvaluationStatus.ERROR)
                    .count();
            
            if (failedCount > 0) {
                completeRunWithStatus(run, EvaluationRun.RunStatus.FAILED);
                log.info("Run {} completed with FAILED status after manual review", runId);
            } else {
                completeRunWithStatus(run, EvaluationRun.RunStatus.COMPLETED);
                log.info("Run {} completed with COMPLETED status after manual review", runId);
            }
            
            Map<String, Integer> statusCounts = new HashMap<>();
            for (EvaluationResult result : allResults) {
                String statusKey = result.getStatus().toString();
                statusCounts.put(statusKey, statusCounts.getOrDefault(statusKey, 0) + 1);
            }
            run.setStatusCounts(statusCounts);
            runRepository.save(run);
        }
    }
}