package ai.driftkit.workflows.spring.service;

import ai.driftkit.common.utils.TextSimilarityUtil;
import ai.driftkit.workflows.core.domain.enhanced.EnhancedReasoningPlan;
import ai.driftkit.workflows.spring.domain.ChecklistItemEntity;
import ai.driftkit.workflows.spring.repository.ChecklistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Service for managing checklist items in the reasoning-lite workflow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistService {

    private final ChecklistItemRepository checklistItemRepository;

    private static final double SIMILARITY_THRESHOLD = 0.85;
    private static final int DEFAULT_LIMIT = 300;
    
    // Cache for promptIds with expiry time
    private final AtomicReference<LocalDateTime> promptIdsCacheTime = new AtomicReference<>(LocalDateTime.MIN);
    private final AtomicReference<List<String>> promptIdsCache = new AtomicReference<>(new ArrayList<>());
    private static final long CACHE_DURATION_MINUTES = 5;

    /**
     * Save checklist items from an EnhancedReasoningPlan
     * Fast implementation - just stores items without similarity checks
     *
     * @param plan     The EnhancedReasoningPlan containing checklist items
     * @param promptId The ID of the prompt that generated this plan
     * @param query    The original query
     */
    public void saveChecklistItems(EnhancedReasoningPlan plan, String promptId, String query) {
        if (plan == null || plan.getChecklist() == null || plan.getChecklist().isEmpty()) {
            log.warn("No checklist items to save");
            return;
        }

        if (query == null || query.isEmpty()) {
            log.error("Query cannot be null or empty when saving checklist items");
            return;
        }

        log.info("Saving {} checklist items for promptId: {}, query: {}", 
                plan.getChecklist().size(), promptId, query);

        List<ChecklistItemEntity> entities = new ArrayList<>();

        for (EnhancedReasoningPlan.ChecklistItem item : plan.getChecklist()) {
            String normalizedDescription = TextSimilarityUtil.normalizeText(item.getDescription());
            
            ChecklistItemEntity entity = ChecklistItemEntity.builder()
                    .description(item.getDescription())
                    .severity(item.getSeverity().name())
                    .promptId(promptId)
                    .query(query)
                    .workflowType("reasoning-lite")
                    .createdAt(LocalDateTime.now())
                    .useCount(1)
                    .normalizedDescription(normalizedDescription)
                    .build();
                    
            entities.add(entity);
        }

        // Just save them all without similarity checks - this is fast
        checklistItemRepository.saveAll(entities);
        log.info("Saved {} checklist items", entities.size());
    }

    /**
     * Scheduled job to process newly added checklist items
     * Runs every hour to find similar items
     */
    @Scheduled(cron = "0 0 * * * ?") // Every hour
    public void processSimilarities() {
        log.info("Starting scheduled similarity processing for checklist items");
        
        // Get unprocessed items (where similarToId is null) with pagination
        List<ChecklistItemEntity> unprocessedItems = checklistItemRepository.findBySimilarToIdIsNull(PageRequest.of(0, 1000));
        
        if (unprocessedItems.isEmpty()) {
            log.info("No unprocessed items found for similarity check");
            return;
        }
        
        log.info("Processing {} unprocessed checklist items", unprocessedItems.size());
        
        // Group by workflowType and promptId to process similar items together
        Map<String, List<ChecklistItemEntity>> groupedItems = unprocessedItems.stream()
                .collect(Collectors.groupingBy(item -> {
                    String workflowType = item.getWorkflowType() != null ? item.getWorkflowType() : "";
                    String promptId = item.getPromptId() != null ? item.getPromptId() : "";
                    return workflowType + ":" + promptId;
                }));
        
        int processedCount = 0;
        List<ChecklistItemEntity> updatedItems = new ArrayList<>();
        
        // Process each group separately
        for (List<ChecklistItemEntity> group : groupedItems.values()) {
            if (group.size() <= 1) {
                continue; // Skip groups with only one item
            }
            
            // Process the group
            for (int i = 0; i < group.size(); i++) {
                ChecklistItemEntity current = group.get(i);
                
                // Skip if already processed
                if (current.getSimilarToId() != null) {
                    continue;
                }
                
                // Compare with other items in the same group
                for (int j = i + 1; j < group.size(); j++) {
                    ChecklistItemEntity other = group.get(j);
                    
                    // Skip if already processed
                    if (other.getSimilarToId() != null) {
                        continue;
                    }
                    
                    // Calculate similarity
                    double similarity = TextSimilarityUtil.calculateSimilarity(
                            current.getNormalizedDescription(), 
                            other.getNormalizedDescription());
                    
                    if (similarity >= SIMILARITY_THRESHOLD) {
                        // Mark the second item as similar to the first
                        other.setSimilarToId(current.getId());
                        other.setSimilarityScore(similarity);
                        updatedItems.add(other);
                        processedCount++;
                    }
                }
            }
        }
        
        // Save all updated items
        if (!updatedItems.isEmpty()) {
            checklistItemRepository.saveAll(updatedItems);
        }
        
        log.info("Similarity processing completed: updated {} items", processedCount);
    }

    /**
     * Scheduled job to clean up duplicate checklist items
     * Runs once a day to remove redundant items
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3 AM every day
    public void cleanupDuplicateItems() {
        log.info("Starting scheduled cleanup of duplicate checklist items");
        
        // Get items marked as similar to others with pagination
        List<ChecklistItemEntity> similarItems = checklistItemRepository.findBySimilarToIdIsNotNull(PageRequest.of(0, 1000));
        
        if (similarItems.isEmpty()) {
            log.info("No duplicate items found to clean up");
            return;
        }
        
        // Group by similarToId
        Map<String, List<ChecklistItemEntity>> similarGroups = similarItems.stream()
                .collect(Collectors.groupingBy(ChecklistItemEntity::getSimilarToId));
        
        int removedCount = 0;
        List<String> idsToRemove = new ArrayList<>();
        List<ChecklistItemEntity> itemsToUpdate = new ArrayList<>();
        
        // Process each group
        for (Map.Entry<String, List<ChecklistItemEntity>> entry : similarGroups.entrySet()) {
            String primaryId = entry.getKey();
            List<ChecklistItemEntity> duplicates = entry.getValue();
            
            // Find the primary item
            Optional<ChecklistItemEntity> primaryOpt = checklistItemRepository.findById(primaryId);
            
            if (primaryOpt.isPresent()) {
                ChecklistItemEntity primary = primaryOpt.get();
                
                // Get items with very high similarity (above 0.95)
                List<ChecklistItemEntity> highSimilarityItems = duplicates.stream()
                        .filter(item -> item.getSimilarityScore() != null && item.getSimilarityScore() > 0.95)
                        .collect(Collectors.toList());
                
                // Sum the use counts of high similarity items
                int additionalUseCount = highSimilarityItems.stream()
                        .mapToInt(ChecklistItemEntity::getUseCount)
                        .sum();
                
                // Update the primary item's use count
                primary.setUseCount(primary.getUseCount() + additionalUseCount);
                itemsToUpdate.add(primary);
                
                // Add high similarity duplicate IDs to remove list
                idsToRemove.addAll(highSimilarityItems.stream()
                        .map(ChecklistItemEntity::getId)
                        .collect(Collectors.toList()));
                
                removedCount += highSimilarityItems.size();
            }
        }
        
        // Save updated items and delete duplicates in batch operations
        if (!itemsToUpdate.isEmpty()) {
            checklistItemRepository.saveAll(itemsToUpdate);
        }
        
        if (!idsToRemove.isEmpty()) {
            checklistItemRepository.deleteAllById(idsToRemove);
        }
        
        log.info("Cleanup completed: removed {} duplicate checklist items", removedCount);
    }

    /**
     * Get all checklist items by promptId with default limit
     */
    public List<ChecklistItemEntity> getChecklistItemsByPromptId(String promptId) {
        return checklistItemRepository.findByPromptId(promptId, PageRequest.of(0, DEFAULT_LIMIT));
    }

    /**
     * Get all checklist items by query with default limit
     */
    public List<ChecklistItemEntity> getChecklistItemsByQuery(String query) {
        return checklistItemRepository.findByQuery(query, PageRequest.of(0, DEFAULT_LIMIT));
    }
    
    /**
     * Get all checklist items by promptId or query with default limit
     */
    public List<ChecklistItemEntity> getChecklistItems(String promptId, String query) {
        if (promptId != null && !promptId.isEmpty()) {
            return checklistItemRepository.findByPromptId(promptId, PageRequest.of(0, DEFAULT_LIMIT));
        } else if (query != null && !query.isEmpty()) {
            return checklistItemRepository.findByQuery(query, PageRequest.of(0, DEFAULT_LIMIT));
        }
        return List.of();
    }
    
    /**
     * Search checklist items by various criteria with default limit
     * 
     * @param promptId Optional promptId to filter by
     * @param query Optional query to filter by (partial match)
     * @param description Optional description to filter by (partial match)
     * @return List of matching checklist items, limited to DEFAULT_LIMIT
     */
    public List<ChecklistItemEntity> searchChecklistItems(String promptId, String query, String description) {
        List<ChecklistItemEntity> results = new ArrayList<>();
        
        // If promptId is provided, that's the most specific search
        if (promptId != null && !promptId.isEmpty()) {
            results.addAll(checklistItemRepository.findByPromptId(promptId, PageRequest.of(0, DEFAULT_LIMIT)));
        } 
        // If query is provided, search by query
        else if (query != null && !query.isEmpty()) {
            results.addAll(checklistItemRepository.findByQueryContaining(query, PageRequest.of(0, DEFAULT_LIMIT)));
        } 
        // If neither promptId nor query is provided, but description is
        else if (description != null && !description.isEmpty()) {
            results.addAll(checklistItemRepository.findByDescriptionContaining(description, PageRequest.of(0, DEFAULT_LIMIT)));
        } 
        // If no search criteria provided, return first PAGE_SIZE items without duplicates
        else {
            return checklistItemRepository.findBySimilarToIdIsNull(PageRequest.of(0, DEFAULT_LIMIT));
        }
        
        // If description is also provided, filter the results further
        if (description != null && !description.isEmpty() && (promptId != null || query != null)) {
            results = results.stream()
                    .filter(item -> item.getDescription().toLowerCase().contains(description.toLowerCase()))
                    .limit(DEFAULT_LIMIT)
                    .collect(Collectors.toList());
        }
        
        return results;
    }
    
    /**
     * Get all unique promptIds that have checklist items, with caching
     */
    public List<String> getAllPromptIds() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cacheTime = promptIdsCacheTime.get();
        
        // Check if cache is still valid (less than CACHE_DURATION_MINUTES old)
        if (ChronoUnit.MINUTES.between(cacheTime, now) < CACHE_DURATION_MINUTES) {
            return new ArrayList<>(promptIdsCache.get());
        }
        
        // Cache expired, fetch fresh data
        List<String> promptIds = checklistItemRepository.findDistinctPromptIds();
        
        // Remove duplicates
        Set<String> uniquePromptIds = new HashSet<>(promptIds);
        List<String> result = new ArrayList<>(uniquePromptIds);
        
        // Update cache
        promptIdsCache.set(result);
        promptIdsCacheTime.set(now);
        
        return result;
    }
    
    /**
     * Update an existing checklist item
     * 
     * @param item The updated checklist item
     * @return The saved item
     */
    public ChecklistItemEntity updateChecklistItem(ChecklistItemEntity item) {
        // First check if item exists
        if (!checklistItemRepository.existsById(item.getId())) {
            throw new IllegalArgumentException("Checklist item not found with ID: " + item.getId());
        }
        
        // Ensure normalized description is updated
        if (item.getDescription() != null) {
            item.setNormalizedDescription(TextSimilarityUtil.normalizeText(item.getDescription()));
        }
        
        // Save the updated item
        return checklistItemRepository.save(item);
    }
    
    /**
     * Delete a checklist item by ID
     * 
     * @param id The ID of the checklist item to delete
     */
    public void deleteChecklistItem(String id) {
        // First check if item exists
        if (!checklistItemRepository.existsById(id)) {
            throw new IllegalArgumentException("Checklist item not found with ID: " + id);
        }
        
        // Check if other items reference this one as similar
        List<ChecklistItemEntity> similarItems = checklistItemRepository.findBySimilarToId(id, PageRequest.of(0, DEFAULT_LIMIT));
        
        // If there are items that reference this one, clear their references
        if (!similarItems.isEmpty()) {
            for (ChecklistItemEntity similarItem : similarItems) {
                similarItem.setSimilarToId(null);
                similarItem.setSimilarityScore(null);
                checklistItemRepository.save(similarItem);
            }
        }
        
        // Now delete the item
        checklistItemRepository.deleteById(id);
    }
}