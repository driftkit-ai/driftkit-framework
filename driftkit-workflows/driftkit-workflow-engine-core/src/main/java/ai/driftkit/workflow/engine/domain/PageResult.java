package ai.driftkit.workflow.engine.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Simple page result abstraction without Spring dependencies.
 * Represents a page of data with pagination information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    
    public int getTotalPages() {
        return (int) Math.ceil((double) totalElements / pageSize);
    }
    
    public static <T> PageResult<T> empty(int pageNumber, int pageSize) {
        return PageResult.<T>builder()
                .content(List.of())
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(0)
                .build();
    }
    
    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
    
    public boolean isFirst() {
        return pageNumber == 0;
    }
    
    public boolean isLast() {
        return pageNumber >= getTotalPages() - 1;
    }
    
    public boolean hasNext() {
        return pageNumber < getTotalPages() - 1;
    }
    
    public boolean hasPrevious() {
        return pageNumber > 0;
    }
}