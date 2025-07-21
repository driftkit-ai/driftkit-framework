package ai.driftkit.workflows.spring.controller;

import ai.driftkit.common.domain.RestResponse;
import ai.driftkit.workflows.spring.domain.ChecklistItemEntity;
import ai.driftkit.workflows.spring.service.ChecklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for accessing and searching checklists
 */
@Slf4j
@RestController
@RequestMapping("/data/v1.0/admin/llm/checklists")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;

    /**
     * Search checklist items with optional filters
     * 
     * @param promptId Optional promptId to filter by
     * @param query Optional query text to filter by (partial match)
     * @param description Optional description text to filter by (partial match)
     * @param includeSimilar Whether to include items marked as similar to others
     * @return List of matching checklist items
     */
    @GetMapping("/search")
    public RestResponse<List<ChecklistItemEntity>> searchChecklists(
            @RequestParam(required = false) String promptId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String description,
            @RequestParam(required = false, defaultValue = "false") boolean includeSimilar) {
        
        log.info("Searching for checklists with promptId: {}, query: {}, description: {}, includeSimilar: {}", 
                promptId, query, description, includeSimilar);
        
        List<ChecklistItemEntity> results = checklistService.searchChecklistItems(promptId, query, description);
        
        // Filter out items marked as similar if requested
        if (!includeSimilar) {
            results = results.stream()
                    .filter(item -> item.getSimilarToId() == null)
                    .collect(Collectors.toList());
        }
        
        return new RestResponse<>(true, results);
    }

    /**
     * Get all available promptIds that have checklist items
     */
    @GetMapping("/prompt-ids")
    public RestResponse<List<String>> getPromptIds() {
        List<String> promptIds = checklistService.getAllPromptIds();
        return new RestResponse<>(true, promptIds);
    }
    
    /**
     * Update an existing checklist item
     * 
     * @param id Checklist item ID
     * @param item Updated checklist item data
     * @return Updated item
     */
    @PostMapping("/{id}")
    public RestResponse<ChecklistItemEntity> updateChecklistItem(
            @PathVariable String id,
            @RequestBody ChecklistItemEntity item) {
        
        if (!id.equals(item.getId())) {
            return new RestResponse<>(false, null);
        }
        
        ChecklistItemEntity updatedItem = checklistService.updateChecklistItem(item);
        return new RestResponse<>(true, updatedItem);
    }
    
    /**
     * Delete a checklist item
     * 
     * @param id Checklist item ID to delete
     * @return Success response
     */
    @DeleteMapping("/{id}")
    public RestResponse<String> deleteChecklistItem(@PathVariable String id) {
        checklistService.deleteChecklistItem(id);
        return new RestResponse<>(true, "Checklist item deleted successfully");
    }
}