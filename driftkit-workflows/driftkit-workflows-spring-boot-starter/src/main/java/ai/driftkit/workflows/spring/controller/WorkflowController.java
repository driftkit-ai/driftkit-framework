package ai.driftkit.workflows.spring.controller;

import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.workflows.core.service.WorkflowRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for providing information about available workflows in the system.
 */
@Controller
@RequestMapping(path = "/data/v1.0/admin/workflows")
public class WorkflowController {

    @Autowired
    private WorkflowRegistry workflowRegistry;

    /**
     * Gets all available workflows.
     * 
     * @return A list of all registered workflows with their id, name, and description
     */
    @GetMapping
    public @ResponseBody RestResponse<List<Map<String, String>>> getWorkflows() {
        List<Map<String, String>> workflows = workflowRegistry.getAllWorkflows()
                .stream()
                .map(WorkflowRegistry.RegisteredWorkflow::toMap)
                .collect(Collectors.toList());
        
        return new RestResponse<>(true, workflows);
    }
}