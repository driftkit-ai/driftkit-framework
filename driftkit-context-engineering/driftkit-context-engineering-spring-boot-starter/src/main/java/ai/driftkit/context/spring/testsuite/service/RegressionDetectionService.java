package ai.driftkit.context.spring.testsuite.service;

import ai.driftkit.context.spring.testsuite.domain.PipelineTestRun;
import ai.driftkit.context.spring.testsuite.repository.PipelineTestRunRepository;
import ai.driftkit.workflow.engine.core.pipeline.PipelineDefinition;
import ai.driftkit.workflow.engine.core.pipeline.PipelineRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled service that runs regression tests on all registered pipelines
 * and compares results with the previous run to detect regressions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegressionDetectionService {

    private final PipelineTestService pipelineTestService;
    private final PipelineTestRunRepository runRepository;

    /**
     * Run regression tests nightly at 3 AM.
     * For each pipeline that has previous test runs, re-run with the same dataset
     * and compare pass rates.
     */
    @Scheduled(cron = "${driftkit.regression.cron:0 0 3 * * ?}")
    public void runRegressionTests() {
        log.info("Starting scheduled regression test run");

        List<PipelineDefinition> pipelines = PipelineRegistry.getInstance().list();
        int regressions = 0;

        for (PipelineDefinition pipeline : pipelines) {
            try {
                List<PipelineTestRun> previousRuns = runRepository.findByPipelineId(pipeline.getId());
                if (previousRuns.isEmpty()) {
                    continue; // No previous runs — nothing to compare
                }

                // Find last completed run
                PipelineTestRun lastRun = previousRuns.stream()
                        .filter(r -> r.getStatus() == PipelineTestRun.RunStatus.COMPLETED
                                  || r.getStatus() == PipelineTestRun.RunStatus.FAILED)
                        .max(Comparator.comparingLong(PipelineTestRun::getCompletedAt))
                        .orElse(null);

                if (lastRun == null || lastRun.getDatasetId() == null) {
                    continue;
                }

                // Re-run with same dataset, no overrides (uses current production prompts)
                PipelineTestRun newRun = pipelineTestService.createRun(
                        pipeline.getId(), lastRun.getDatasetId(), Collections.emptyMap());

                log.info("Regression test started for pipeline '{}', run ID: {}", pipeline.getId(), newRun.getId());

                // Note: comparison happens after run completes asynchronously.
                // For now, log the trigger. Full comparison would poll for completion
                // and compare newRun.passedCases vs lastRun.passedCases.

                // Simple synchronous check (wait up to 5 minutes)
                PipelineTestRun completed = waitForCompletion(newRun.getId(), 300_000);
                if (completed != null && lastRun.getTotalCases() > 0) {
                    double oldRate = (double) lastRun.getPassedCases() / lastRun.getTotalCases() * 100;
                    double newRate = completed.getTotalCases() > 0
                            ? (double) completed.getPassedCases() / completed.getTotalCases() * 100
                            : 0;

                    if (newRate < oldRate) {
                        regressions++;
                        log.warn("REGRESSION DETECTED in pipeline '{}': pass rate dropped from {}% to {}%",
                                pipeline.getId(), oldRate, newRate);
                    } else {
                        log.info("Pipeline '{}': pass rate {}% (was {}%)",
                                pipeline.getId(), newRate, oldRate);
                    }
                }

            } catch (Exception e) {
                log.error("Error running regression test for pipeline '{}': {}", pipeline.getId(), e.getMessage());
            }
        }

        log.info("Regression test run completed. {} regressions detected out of {} pipelines",
                regressions, pipelines.size());
    }

    private PipelineTestRun waitForCompletion(String runId, long timeoutMs) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                while (true) {
                    PipelineTestRun run = runRepository.findById(runId).orElse(null);
                    if (run != null && (run.getStatus() == PipelineTestRun.RunStatus.COMPLETED
                                     || run.getStatus() == PipelineTestRun.RunStatus.FAILED)) {
                        return run;
                    }
                    try { Thread.sleep(5000); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); return null;
                    }
                }
            }).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Timeout waiting for pipeline test run {}", runId);
            return null;
        }
    }
}
