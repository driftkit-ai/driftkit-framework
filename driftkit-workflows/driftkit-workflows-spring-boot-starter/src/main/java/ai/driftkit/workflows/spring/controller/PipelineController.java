package ai.driftkit.workflows.spring.controller;

import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.workflow.engine.core.pipeline.PipelineDefinition;
import ai.driftkit.workflow.engine.core.pipeline.PipelineRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping(path = "/data/v1.0/admin/pipelines")
public class PipelineController {

    @GetMapping("/")
    public @ResponseBody RestResponse<List<PipelineDefinition>> listPipelines() {
        return new RestResponse<>(true, PipelineRegistry.getInstance().list());
    }

    @GetMapping("/{id}")
    public @ResponseBody RestResponse<PipelineDefinition> getPipeline(@PathVariable String id) {
        return PipelineRegistry.getInstance().get(id)
                .map(p -> new RestResponse<>(true, p))
                .orElse(new RestResponse<>(false, null, "Pipeline not found: " + id));
    }
}
