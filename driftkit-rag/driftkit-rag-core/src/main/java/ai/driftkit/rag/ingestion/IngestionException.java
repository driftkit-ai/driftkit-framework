package ai.driftkit.rag.ingestion;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception that accumulates multiple errors during ingestion process.
 */
@Getter
public class IngestionException extends Exception {
    
    private final List<ErrorDetail> errors;
    
    /**
     * Error detail for a specific document.
     */
    public record ErrorDetail(
            String documentId,
            String source,
            Exception error,
            ErrorType errorType
    ) {}
    
    /**
     * Type of error that occurred.
     */
    public enum ErrorType {
        DOCUMENT_PROCESSING,
        RESULT_HANDLER,
        PIPELINE_INITIALIZATION
    }
    
    public IngestionException(String message, List<ErrorDetail> errors) {
        super(message + " (" + errors.size() + " errors)");
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }
    
    public IngestionException(String message, Throwable cause) {
        super(message, cause);
        this.errors = List.of(new ErrorDetail("unknown", "unknown", 
            cause instanceof Exception ? (Exception) cause : new RuntimeException(cause),
            ErrorType.PIPELINE_INITIALIZATION));
    }
    
    /**
     * Check if there were any critical errors (non-result handler errors).
     */
    public boolean hasCriticalErrors() {
        return errors.stream()
            .anyMatch(e -> e.errorType() != ErrorType.RESULT_HANDLER);
    }
    
    /**
     * Get only critical errors.
     */
    public List<ErrorDetail> getCriticalErrors() {
        return errors.stream()
            .filter(e -> e.errorType() != ErrorType.RESULT_HANDLER)
            .toList();
    }
    
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        sb.append("\nError details:");
        
        for (ErrorDetail error : errors) {
            sb.append("\n  - Document: ").append(error.documentId())
              .append(" (").append(error.errorType()).append("): ")
              .append(error.error().getMessage());
        }
        
        return sb.toString();
    }
}