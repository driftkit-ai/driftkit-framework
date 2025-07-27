package ai.driftkit.chat.framework.service.impl;

import ai.driftkit.chat.framework.model.ChatDomain.ChatResponse;
import ai.driftkit.chat.framework.service.AsyncResponseTracker;
import ai.driftkit.chat.framework.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Default implementation of AsyncResponseTracker.
 * Service for tracking and managing asynchronous responses.
 * Allows tasks to execute in the background while providing progress updates.
 * Uses a cache for quick access with database fallback for persistence.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(AsyncResponseTracker.class)
public class DefaultAsyncResponseTracker implements AsyncResponseTracker {
    // Cache for active responses - cleared periodically, DB is source of truth
    private final Map<String, ChatResponse> responseCache = new ConcurrentHashMap<>();
    // Cache for executing tasks
    private final Map<String, CompletableFuture<?>> runningTasks = new ConcurrentHashMap<>();
    
    // Maximum age for cached items in milliseconds (30 minutes)
    private static final long MAX_CACHE_AGE_MS = TimeUnit.MINUTES.toMillis(30);
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ChatHistoryService historyService;
    
    public DefaultAsyncResponseTracker(ChatHistoryService historyService) {
        this.historyService = historyService;
    }
    
    /**
     * Generate a unique response ID for tracking
     */
    @Override
    public String generateResponseId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Track a response for later retrieval and status updates
     */
    @Override
    public void trackResponse(String responseId, ChatResponse response) {
        try {
            // Ensure consistent ID
            if (!responseId.equals(response.getId())) {
                response.setId(responseId);
            }
            
            // Save to persistent storage (DB)
            historyService.updateResponse(response);
            
            // Update cache after successful DB save
            responseCache.put(responseId, response);
            
            log.debug("Tracking response: {} for session: {}", responseId, response.getChatId());
        } catch (Exception e) {
            log.error("Error tracking response: {}", responseId, e);
        }
    }
    
    /**
     * Get a tracked response by ID
     */
    @Override
    public Optional<ChatResponse> getResponse(String responseId) {
        try {
            // Check cache first
            ChatResponse response = responseCache.get(responseId);
            
            // If not in cache, query from database
            if (response == null) {
                response = historyService.getResponse(responseId);
                
                // If found in DB, update cache
                if (response != null) {
                    responseCache.put(responseId, response);
                }
            }
            
            if (response != null) {
                log.debug("Found response: {} with completion status: {}", 
                         responseId, response.isCompleted());
            } else {
                log.debug("Response not found: {}", responseId);
            }
            
            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.error("Error getting response: {}", responseId, e);
            return Optional.empty();
        }
    }

    @Override
    public void removeResponse(String responseId) {
        responseCache.remove(responseId);
        runningTasks.remove(responseId);
    }

    @Override
    public void updateResponseStatus(String responseId, ChatResponse response) {
        updateResponseStatus(responseId, response.getPercentComplete(), response.isCompleted());
    }

    /**
     * Update the status of a tracked response
     */
    public void updateResponseStatus(String responseId, int percentComplete, boolean completed) {
        try {
            Optional<ChatResponse> responseOpt = getResponse(responseId);
            if (responseOpt.isPresent()) {
                ChatResponse response = responseOpt.get();
                
                // Only update if the status has changed
                if (response.getPercentComplete() != percentComplete || 
                    response.isCompleted() != completed) {
                    
                    // Update status
                    response.setPercentComplete(percentComplete);
                    response.setCompleted(completed);
                    
                    // Update in database first
                    historyService.updateResponse(response);
                    
                    // Update cache after successful DB update
                    responseCache.put(responseId, response);
                    
                    log.debug("Updated response status: {} to {}% complete, completed: {}", 
                             responseId, percentComplete, completed);
                }
            } else {
                log.warn("Cannot update status - response not found: {}", responseId);
            }
        } catch (Exception e) {
            log.error("Error updating response status: {}", responseId, e);
        }
    }
    
    /**
     * Execute a task asynchronously and track its progress
     */
    @Override
    public <T extends ChatResponse> CompletableFuture<T> executeAsync(
            String responseId, 
            T initialResponse,
            Supplier<T> task) {
        
        try {
            // Make sure the initial response is tracked
            trackResponse(responseId, initialResponse);
            
            // Execute the task asynchronously
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    log.info("Starting async task for response: {}", responseId);
                    
                    // Execute the task
                    T result = task.get();
                    
                    // Update tracking with final result
                    if (result != null) {
                        result.setId(responseId);
                        trackResponse(responseId, result);
                        
                        log.info("Async task completed successfully for response: {}", responseId);
                    } else {
                        // Handle null result
                        log.error("Async task returned null result for response: {}", responseId);
                        
                        // Update the initial response to show an error
                        ChatResponse errorResponse = getResponse(responseId).orElse(initialResponse);
                        errorResponse.updateOrAddProperty("error", "Task returned null result");
                        errorResponse.setCompleted(true);
                        errorResponse.setPercentComplete(100);
                        
                        // Update tracking
                        trackResponse(responseId, errorResponse);
                        
                        // Return the error response cast to expected type
                        @SuppressWarnings("unchecked")
                        T typedErrorResponse = (T) errorResponse;
                        return typedErrorResponse;
                    }
                    
                    return result;
                } catch (Exception e) {
                    // Handle exceptions
                    log.error("Error executing async task for response: {}", responseId, e);
                    
                    // Update the initial response to show an error
                    ChatResponse errorResponse = getResponse(responseId).orElse(initialResponse);
                    errorResponse.updateOrAddProperty("error", e.getMessage());
                    errorResponse.setCompleted(true);
                    errorResponse.setPercentComplete(100);
                    
                    // Update tracking
                    trackResponse(responseId, errorResponse);
                    
                    // Return the error response cast to expected type
                    @SuppressWarnings("unchecked")
                    T typedErrorResponse = (T) errorResponse;
                    return typedErrorResponse;
                } finally {
                    // Remove from running tasks when complete
                    runningTasks.remove(responseId);
                }
            }, executorService);
            
            // Track the running task for cleanup
            runningTasks.put(responseId, future);
            
            return future;
        } catch (Exception e) {
            log.error("Error setting up async task for response: {}", responseId, e);
            
            // Create error response
            ChatResponse errorResponse = initialResponse;
            errorResponse.updateOrAddProperty("error", "Failed to start async task: " + e.getMessage());
            errorResponse.setCompleted(true);
            errorResponse.setPercentComplete(100);
            
            // Update tracking
            trackResponse(responseId, errorResponse);
            
            // Return a completed future with the error
            @SuppressWarnings("unchecked")
            T typedErrorResponse = (T) errorResponse;
            return CompletableFuture.completedFuture(typedErrorResponse);
        }
    }
    
    /**
     * Clean up old entries to prevent memory leaks
     * Run every 30 minutes
     */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void cleanup() {
        try {
            long now = System.currentTimeMillis();
            long cutoffTime = now - MAX_CACHE_AGE_MS;
            int initialSize = responseCache.size();
            
            // Clean up completed responses
            responseCache.entrySet().removeIf(entry -> {
                ChatResponse response = entry.getValue();
                // Remove if it's completed and old
                return response.isCompleted() && response.getTimestamp() < cutoffTime;
            });
            
            // Log cleanup results
            log.info("Response cache cleanup: removed {} entries, {} remaining", 
                    initialSize - responseCache.size(),
                    responseCache.size());
        } catch (Exception e) {
            log.error("Error during response cache cleanup", e);
        }
    }
}