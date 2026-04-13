package ai.driftkit.context.spring.testsuite.service;

import ai.driftkit.common.domain.Language;
import ai.driftkit.common.domain.MessageTask;
import ai.driftkit.context.core.service.PromptOverrideContext;
import ai.driftkit.context.spring.testsuite.domain.*;
import ai.driftkit.context.spring.testsuite.domain.archive.TestSetItemImpl;
import ai.driftkit.context.spring.testsuite.repository.*;
import ai.driftkit.workflow.engine.core.pipeline.PipelineDefinition;
import ai.driftkit.workflow.engine.core.pipeline.PipelineRegistry;
import ai.driftkit.workflows.spring.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for running pipeline-level tests: executes a full pipeline for each test case
 * with optional prompt overrides per step, captures step-level traces.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineTestService {

    private final PipelineTestRunRepository runRepository;
    private final PipelineTestResultRepository resultRepository;
    private final TestSetItemRepository testSetItemRepository;
    private final AIService aiService;

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }

    /**
     * Create and start a pipeline test run.
     */
    public PipelineTestRun createRun(String pipelineId, String datasetId, Map<String, String> promptOverrides) {
        Optional<PipelineDefinition> pipelineOpt = PipelineRegistry.getInstance().get(pipelineId);
        if (pipelineOpt.isEmpty()) {
            throw new IllegalArgumentException("Pipeline not found: " + pipelineId);
        }

        PipelineTestRun run = PipelineTestRun.builder()
                .pipelineId(pipelineId)
                .datasetId(datasetId)
                .promptOverrides(promptOverrides)
                .status(PipelineTestRun.RunStatus.QUEUED)
                .createdAt(System.currentTimeMillis())
                .build();

        run = runRepository.save(run);

        final String runId = run.getId();
        executor.submit(() -> executeRun(runId));

        return run;
    }

    /**
     * Execute a pipeline test run: for each test case, run the pipeline with prompt overrides
     * and evaluate results.
     */
    private void executeRun(String runId) {
        PipelineTestRun run = runRepository.findById(runId).orElse(null);
        if (run == null) return;

        run.setStatus(PipelineTestRun.RunStatus.RUNNING);
        runRepository.save(run);

        try {
            List<TestSetItemImpl> items = testSetItemRepository.findByTestSetId(run.getDatasetId());
            run.setTotalCases(items.size());

            PipelineDefinition pipeline = PipelineRegistry.getInstance().get(run.getPipelineId()).orElse(null);
            if (pipeline == null) {
                run.setStatus(PipelineTestRun.RunStatus.FAILED);
                run.setCompletedAt(System.currentTimeMillis());
                runRepository.save(run);
                return;
            }

            int passed = 0;
            int failed = 0;
            long totalLatency = 0;
            int totalTokens = 0;

            for (TestSetItemImpl item : items) {
                long startTime = System.currentTimeMillis();

                PipelineTestResult result = PipelineTestResult.builder()
                        .runId(runId)
                        .testCaseId(item.getId())
                        .input(item.getMessage())
                        .expectedOutput(item.getResult())
                        .stepTraces(new ArrayList<>())
                        .build();

                try {
                    // Set prompt overrides for this thread — PromptService will use them
                    Map<String, String> overrides = run.getPromptOverrides();
                    if (overrides != null && !overrides.isEmpty()) {
                        PromptOverrideContext.set(overrides);
                    }

                    // Build MessageTask and execute via AIService
                    MessageTask task = new MessageTask();
                    task.setMessage(item.getMessage());
                    task.setWorkflow(pipeline.getId());
                    task.setLanguage(Language.GENERAL);
                    task.setPurpose("pipeline_test");

                    if (item.getVariables() != null) {
                        task.setVariables(new HashMap<>(item.getVariables()));
                    }
                    if (StringUtils.isNotBlank(item.getModel())) {
                        task.setModelId(item.getModel());
                    }
                    if (item.getTemperature() != null) {
                        task.setTemperature(item.getTemperature());
                    }

                    // Execute — AIService.chat() will check WorkflowRegistry for the pipeline
                    MessageTask executed = aiService.chat(task);

                    String actualResult = executed.getResult();
                    result.setActualOutput(actualResult);

                    // Compare with expected result if provided
                    if (StringUtils.isNotBlank(item.getResult())) {
                        if (StringUtils.isNotBlank(actualResult)
                                && actualResult.trim().contains(item.getResult().trim())) {
                            result.setStatus("PASSED");
                            passed++;
                        } else {
                            result.setStatus("FAILED");
                            failed++;
                        }
                    } else {
                        // No expected result — pass if execution succeeded without error
                        result.setStatus("PASSED");
                        passed++;
                    }

                } catch (Exception e) {
                    result.setStatus("ERROR");
                    result.setErrorMessage(e.getMessage());
                    failed++;
                    log.warn("Pipeline test case {} failed: {}", item.getId(), e.getMessage());
                } finally {
                    PromptOverrideContext.clear();
                }

                result.setLatencyMs(System.currentTimeMillis() - startTime);
                totalLatency += (long) result.getLatencyMs();
                totalTokens += result.getTotalTokens();
                resultRepository.save(result);
            }

            run.setPassedCases(passed);
            run.setFailedCases(failed);
            run.setAvgLatencyMs(items.isEmpty() ? 0 : (double) totalLatency / items.size());
            run.setTotalTokens(totalTokens);
            run.setStatus(failed > 0 ? PipelineTestRun.RunStatus.FAILED : PipelineTestRun.RunStatus.COMPLETED);
            run.setCompletedAt(System.currentTimeMillis());
            runRepository.save(run);

            log.info("Pipeline test run {} completed: {}/{} passed", runId, passed, items.size());

        } catch (Exception e) {
            log.error("Pipeline test run {} failed: {}", runId, e.getMessage(), e);
            run.setStatus(PipelineTestRun.RunStatus.FAILED);
            run.setCompletedAt(System.currentTimeMillis());
            runRepository.save(run);
        }
    }

    public Optional<PipelineTestRun> getRun(String runId) {
        return runRepository.findById(runId);
    }

    public List<PipelineTestResult> getResults(String runId) {
        return resultRepository.findByRunId(runId);
    }

    public List<PipelineTestRun> getRunsForPipeline(String pipelineId) {
        return runRepository.findByPipelineId(pipelineId);
    }
}
