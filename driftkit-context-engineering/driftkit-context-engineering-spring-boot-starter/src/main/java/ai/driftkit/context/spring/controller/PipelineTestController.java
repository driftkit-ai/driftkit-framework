package ai.driftkit.context.spring.controller;

import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.context.spring.testsuite.domain.PipelineTestResult;
import ai.driftkit.context.spring.testsuite.domain.PipelineTestRun;
import ai.driftkit.context.spring.testsuite.service.PipelineTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping(path = "/data/v1.0/admin/pipeline-tests")
public class PipelineTestController {

    private final PipelineTestService pipelineTestService;

    @PostMapping("/run")
    public @ResponseBody RestResponse<PipelineTestRun> createRun(
            @RequestBody RunRequest request
    ) {
        PipelineTestRun run = pipelineTestService.createRun(
                request.pipelineId, request.datasetId, request.promptOverrides);
        return new RestResponse<>(true, run);
    }

    @GetMapping("/{runId}")
    public @ResponseBody RestResponse<PipelineTestRun> getRun(@PathVariable String runId) {
        return pipelineTestService.getRun(runId)
                .map(r -> new RestResponse<>(true, r))
                .orElse(new RestResponse<>(false, null, "Run not found: " + runId));
    }

    @GetMapping("/{runId}/results")
    public @ResponseBody RestResponse<List<PipelineTestResult>> getResults(@PathVariable String runId) {
        return new RestResponse<>(true, pipelineTestService.getResults(runId));
    }

    public record RunRequest(String pipelineId, String datasetId, Map<String, String> promptOverrides) {}
}
