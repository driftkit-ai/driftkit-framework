package ai.driftkit.workflow.engine.spring.adapter;

import ai.driftkit.workflow.engine.domain.PageRequest;
import ai.driftkit.workflow.engine.domain.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Adapter to convert between Spring's Page/Pageable and core PageResult/PageRequest.
 */
public class PageResultAdapter {
    
    /**
     * Convert Spring Pageable to core PageRequest.
     */
    public static PageRequest toPageRequest(Pageable pageable) {
        PageRequest.SortDirection direction = PageRequest.SortDirection.ASC;
        String sortBy = "id";
        
        if (pageable.getSort().isSorted()) {
            Sort.Order order = pageable.getSort().iterator().next();
            sortBy = order.getProperty();
            direction = order.isAscending() 
                ? PageRequest.SortDirection.ASC 
                : PageRequest.SortDirection.DESC;
        }
        
        return PageRequest.builder()
                .pageNumber(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .sortBy(sortBy)
                .sortDirection(direction)
                .build();
    }
    
    /**
     * Convert core PageResult to Spring Page.
     */
    public static <T> Page<T> toPage(PageResult<T> pageResult, Pageable pageable) {
        return new PageImpl<>(
            pageResult.getContent(),
            pageable,
            pageResult.getTotalElements()
        );
    }
    
    /**
     * Convert Spring Page to core PageResult.
     */
    public static <T> PageResult<T> toPageResult(Page<T> page) {
        return PageResult.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .build();
    }
}