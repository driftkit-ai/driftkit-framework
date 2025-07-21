package ai.driftkit.context.spring.testsuite.controller;

import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.context.spring.testsuite.domain.Evaluation;
import ai.driftkit.context.spring.testsuite.domain.EvaluationResult;
import ai.driftkit.context.spring.testsuite.domain.EvaluationRun;
import ai.driftkit.context.spring.testsuite.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controller for managing evaluations
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/data/v1.0/admin")
public class EvaluationController {

    private final EvaluationService evaluationService;
    
    /**
     * Get all global evaluations (not tied to a specific test set)
     */
    @GetMapping("/evaluations/global")
    public RestResponse<List<Evaluation>> getGlobalEvaluations() {
        List<Evaluation> evaluations = evaluationService.getGlobalEvaluations();
        return new RestResponse<>(true, evaluations);
    }
    
    /**
     * Create a new global evaluation
     */
    @PostMapping("/evaluations/global")
    public RestResponse<Evaluation> createGlobalEvaluation(@RequestBody Evaluation evaluation) {
        try {
            log.info("Received global evaluation create request: {}", evaluation);
            // Ensure testSetId is null for global evaluations
            evaluation.setTestSetId(null);
            Evaluation created = evaluationService.createEvaluation(evaluation);
            return new RestResponse<>(true, created);
        } catch (Exception e) {
            log.error("Error creating global evaluation: {}", e.getMessage(), e);
            return new RestResponse<>(false, null, "Error creating global evaluation: " + e.getMessage());
        }
    }
    
    /**
     * Add a global evaluation to a test set (creates a copy)
     */
    @PostMapping("/test-sets/{testSetId}/evaluations/add/{evaluationId}")
    public RestResponse<Evaluation> addEvaluationToTestSet(
            @PathVariable String testSetId, 
            @PathVariable String evaluationId) {
        try {
            log.info("Adding evaluation {} to test set {}", evaluationId, testSetId);
            Evaluation added = evaluationService.copyEvaluation(evaluationId, testSetId);
            return new RestResponse<>(true, added);
        } catch (Exception e) {
            log.error("Error adding evaluation to test set: {}", e.getMessage(), e);
            return new RestResponse<>(false, null, "Error adding evaluation: " + e.getMessage());
        }
    }

    /**
     * Get all evaluations for a test set
     */
    @GetMapping("/test-sets/{testSetId}/evaluations")
    public RestResponse<List<Evaluation>> getEvaluationsForTestSet(@PathVariable String testSetId) {
        List<Evaluation> evaluations = evaluationService.getEvaluationsForTestSet(testSetId);
        return new RestResponse<>(true, evaluations);
    }

    /**
     * Get a specific evaluation
     */
    @GetMapping("/evaluations/{id}")
    public RestResponse<Evaluation> getEvaluation(@PathVariable String id) {
        Optional<Evaluation> evaluation = evaluationService.getEvaluation(id);
        return evaluation.map(value -> new RestResponse<Evaluation>(true, value))
                .orElseGet(() -> new RestResponse<Evaluation>(false, null, "Evaluation not found"));
    }

    /**
     * Create a new evaluation
     */
    @PostMapping("/test-sets/{testSetId}/evaluations")
    public RestResponse<Evaluation> createEvaluation(@PathVariable String testSetId, @RequestBody Evaluation evaluation) {
        try {
            log.info("Received evaluation create request: {}", evaluation);
            evaluation.setTestSetId(testSetId);
            Evaluation created = evaluationService.createEvaluation(evaluation);
            return new RestResponse<>(true, created);
        } catch (Exception e) {
            log.error("Error creating evaluation: {}", e.getMessage(), e);
            return new RestResponse<Evaluation>(false, null, "Error creating evaluation: " + e.getMessage());
        }
    }
    
    /**
     * Copy an evaluation to another test set
     */
    @PostMapping("/test-sets/{targetTestSetId}/evaluations/copy/{evaluationId}")
    public RestResponse<Evaluation> copyEvaluation(@PathVariable String targetTestSetId, @PathVariable String evaluationId) {
        try {
            log.info("Copying evaluation {} to test set {}", evaluationId, targetTestSetId);
            Evaluation copied = evaluationService.copyEvaluation(evaluationId, targetTestSetId);
            return new RestResponse<>(true, copied);
        } catch (Exception e) {
            log.error("Error copying evaluation: {}", e.getMessage(), e);
            return new RestResponse<Evaluation>(false, null, "Error copying evaluation: " + e.getMessage());
        }
    }

    /**
     * Update an evaluation
     */
    @PutMapping("/evaluations/{id}")
    public RestResponse<Evaluation> updateEvaluation(@PathVariable String id, @RequestBody Evaluation evaluation) {
        try {
            Evaluation updated = evaluationService.updateEvaluation(id, evaluation);
            return new RestResponse<>(true, updated);
        } catch (IllegalArgumentException e) {
            return new RestResponse<Evaluation>(false, null, e.getMessage());
        }
    }
    
    /**
     * Update a test set evaluation
     */
    @PutMapping("/test-sets/{testSetId}/evaluations/{id}")
    public RestResponse<Evaluation> updateTestSetEvaluation(
            @PathVariable String testSetId, 
            @PathVariable String id, 
            @RequestBody Evaluation evaluation) {
        try {
            log.info("Updating evaluation {} for test set {}", id, testSetId);
            // Ensure we maintain the test set association
            evaluation.setTestSetId(testSetId);
            Evaluation updated = evaluationService.updateEvaluation(id, evaluation);
            return new RestResponse<>(true, updated);
        } catch (IllegalArgumentException e) {
            return new RestResponse<Evaluation>(false, null, e.getMessage());
        } catch (Exception e) {
            log.error("Error updating test set evaluation: {}", e.getMessage(), e);
            return new RestResponse<Evaluation>(false, null, "Error updating evaluation: " + e.getMessage());
        }
    }

    /**
     * Delete an evaluation
     */
    @DeleteMapping("/evaluations/{id}")
    public RestResponse<Void> deleteEvaluation(@PathVariable String id) {
        evaluationService.deleteEvaluation(id);
        return new RestResponse<>(true, null);
    }

    /**
     * Get all runs for a test set
     */
    @GetMapping("/test-sets/{testSetId}/runs")
    public RestResponse<List<EvaluationRun>> getRunsForTestSet(@PathVariable String testSetId) {
        List<EvaluationRun> runs = evaluationService.getRunsForTestSet(testSetId);
        return new RestResponse<>(true, runs);
    }

    /**
     * Get a specific run
     */
    @GetMapping("/runs/{id}")
    public RestResponse<EvaluationRun> getRun(@PathVariable String id) {
        Optional<EvaluationRun> run = evaluationService.getRun(id);
        return run.map(value -> new RestResponse<EvaluationRun>(true, value))
                .orElseGet(() -> new RestResponse<EvaluationRun>(false, null, "Run not found"));
    }

    /**
     * Create a new run
     */
    @PostMapping("/test-sets/{testSetId}/runs")
    public RestResponse<EvaluationRun> createRun(@PathVariable String testSetId, @RequestBody EvaluationRun run) {
        try {
            log.info("Creating evaluation run for test set {}: {}", testSetId, run);
            
            // Validate the request parameters
            if (StringUtils.isNotBlank(run.getModelId()) && StringUtils.isNotBlank(run.getWorkflow())) {
                return new RestResponse<>(false, null, 
                    "Cannot specify both modelId and workflow. Choose one execution method.");
            }
            
            run.setTestSetId(testSetId);
            EvaluationRun created = evaluationService.createEvaluationRun(run);
            return new RestResponse<>(true, created);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid run configuration: {}", e.getMessage());
            return new RestResponse<>(false, null, e.getMessage());
        } catch (Exception e) {
            log.error("Error creating evaluation run: {}", e.getMessage(), e);
            return new RestResponse<>(false, null, "Error creating run: " + e.getMessage());
        }
    }

    /**
     * Delete a run
     */
    @DeleteMapping("/test-sets/runs/{id}")
    public RestResponse<Void> deleteRun(@PathVariable String id) {
        evaluationService.deleteRun(id);
        return new RestResponse<>(true, null);
    }

    /**
     * Get all results for a run
     */
    @GetMapping("/test-sets/runs/{runId}/results")
    public RestResponse<List<EvaluationResult>> getResultsForRun(@PathVariable String runId) {
        List<EvaluationResult> results = evaluationService.getResultsForRun(runId);
        return new RestResponse<>(true, results);
    }

    /**
     * Start a run
     */
    @PostMapping("/test-sets/runs/{runId}/start")
    public RestResponse<Void> startRun(@PathVariable String runId) {
        evaluationService.executeEvaluationRun(runId);
        return new RestResponse<Void>(true, null, "Run started");
    }
    
    /**
     * Quick run - create and start a new run for a test set
     */
    @PostMapping("/test-sets/{testSetId}/quick-run")
    public RestResponse<EvaluationRun> quickRun(@PathVariable String testSetId) {
        try {
            EvaluationRun run = evaluationService.createAndExecuteRun(testSetId);
            return new RestResponse<>(true, run, "Run created and started");
        } catch (Exception e) {
            log.error("Error creating quick run: {}", e.getMessage(), e);
            return new RestResponse<EvaluationRun>(false, null, "Error creating run: " + e.getMessage());
        }
    }
    
    /**
     * Get all runs across all test sets
     */
    @GetMapping("/test-sets/all-runs")
    public RestResponse<List<EvaluationRun>> getAllRuns() {
        try {
            List<EvaluationRun> runs = evaluationService.getAllRuns();
            return new RestResponse<>(true, runs);
        } catch (Exception e) {
            log.error("Error fetching all runs: {}", e.getMessage(), e);
            return new RestResponse<List<EvaluationRun>>(false, null, "Error fetching runs: " + e.getMessage());
        }
    }
    
    /**
     * Update the status of a manual evaluation result
     */
    @PostMapping("/evaluation-results/{resultId}/manual-review")
    public RestResponse<EvaluationResult> updateManualEvaluationStatus(
            @PathVariable String resultId,
            @RequestBody ManualReviewRequest request) {
        try {
            EvaluationResult result = evaluationService.updateManualEvaluationStatus(
                resultId, request.isPassed(), request.getFeedback());
            return new RestResponse<>(true, result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid manual review update: {}", e.getMessage());
            return new RestResponse<>(false, null, e.getMessage());
        } catch (Exception e) {
            log.error("Error updating manual evaluation status: {}", e.getMessage(), e);
            return new RestResponse<>(false, null, "Error updating status: " + e.getMessage());
        }
    }
    
    /**
     * Request body for manual review updates
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ManualReviewRequest {
        private boolean passed;
        private String feedback;
    }
}