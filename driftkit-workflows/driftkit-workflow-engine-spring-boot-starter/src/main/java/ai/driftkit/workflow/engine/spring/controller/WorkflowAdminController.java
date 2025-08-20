package ai.driftkit.workflow.engine.spring.controller;

import ai.driftkit.workflow.engine.domain.WorkflowMetadata;
import ai.driftkit.workflow.engine.spring.dto.RestResponse;
import ai.driftkit.workflow.engine.spring.service.WorkflowService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping(path = "/data/v1.0/admin/workflows")
public class WorkflowAdminController {

    private final WorkflowService workflowService;

    @GetMapping
    public @ResponseBody RestResponse<List<WorkflowInfo>> getWorkflows() {
        log.info("Getting all available workflows");
        
        try {
            List<WorkflowMetadata> workflows = workflowService.listWorkflows();
            
            List<WorkflowInfo> workflowInfos = workflows.stream()
                    .map(workflow -> new WorkflowInfo(
                            workflow.id(),
                            workflow.id(), // use id as name for compatibility
                            workflow.description()
                    ))
                    .collect(Collectors.toList());
            
            return new RestResponse<>(true, workflowInfos);
            
        } catch (Exception e) {
            log.error("Error retrieving workflows", e);
            return new RestResponse<>(false, null);
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowInfo {
        private String id;
        private String name;
        private String description;
    }
}