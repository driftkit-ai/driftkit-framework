package ai.driftkit.workflow.engine.async;

/**
 * Unified interface for reporting task progress.
 * This combines the functionality of the former AsyncProgressReporter.
 */
public interface TaskProgressReporter {
    /**
     * Updates the progress of the current operation.
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
    void updateProgress(int percentComplete);
    
    /**
     * Updates just the status message.
     * 
     * @param message Status message
     */
    void updateMessage(String message);
    
    /**
     * Checks if the operation has been cancelled.
     * 
     * @return true if the operation has been cancelled
     */
    boolean isCancelled();
}