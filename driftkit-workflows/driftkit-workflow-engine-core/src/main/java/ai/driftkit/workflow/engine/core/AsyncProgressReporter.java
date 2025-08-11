package ai.driftkit.workflow.engine.core;

/**
 * Interface for reporting progress from within async step handlers.
 * This allows user code to update progress during long-running operations.
 */
public interface AsyncProgressReporter {
    
    /**
     * Updates the progress of the current async operation.
     * 
     * @param percentComplete Progress percentage (0-100)
     * @param message Status message describing current operation
     */
    void updateProgress(int percentComplete, String message);
    
    /**
     * Updates just the progress percentage.
     * 
     * @param percentComplete Progress percentage (0-100)
     */
    default void updateProgress(int percentComplete) {
        updateProgress(percentComplete, "Processing... " + percentComplete + "%");
    }
    
    /**
     * Updates just the status message.
     * 
     * @param message Status message
     */
    default void updateMessage(String message) {
        // Keep current percentage, just update message
        updateProgress(-1, message);
    }
    
    /**
     * Checks if the async operation has been cancelled.
     * User code should check this periodically and exit gracefully if true.
     * 
     * @return true if the operation has been cancelled
     */
    boolean isCancelled();
}