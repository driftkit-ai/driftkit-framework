package ai.driftkit.workflow.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple page request abstraction without Spring dependencies.
 * Represents pagination parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
    @Builder.Default
    private int pageNumber = 0;
    
    @Builder.Default
    private int pageSize = 20;
    
    @Builder.Default
    private String sortBy = "id";
    
    @Builder.Default
    private SortDirection sortDirection = SortDirection.ASC;
    
    public long getOffset() {
        return (long) pageNumber * pageSize;
    }
    
    public static PageRequest of(int pageNumber, int pageSize) {
        return PageRequest.builder()
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .build();
    }
    
    public static PageRequest of(int pageNumber, int pageSize, String sortBy, SortDirection sortDirection) {
        return PageRequest.builder()
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();
    }
    
    public enum SortDirection {
        ASC, DESC
    }
}