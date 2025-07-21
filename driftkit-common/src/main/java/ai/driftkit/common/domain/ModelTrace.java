package ai.driftkit.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Common trace information for model client operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrace {
    // Request execution time
    private long executionTimeMs;
    
    // Error information
    private boolean hasError;
    private String errorMessage;
    
    // Token counts
    private int promptTokens;
    private int completionTokens;
    
    // Model information
    private String model;
    private Double temperature;
    private String responseFormat;
}