package ai.driftkit.chat.framework.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * Response with information about current and available pages
 *
 * @param <DataType> Page data type
 */
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Schema(
        title = "Pageable Response",
        description = "Response with information about current and available pages"
)
public class PageableResponse<DataType> {

    /**
     * Page data
     */
    @NotNull
    @Schema(
            description = "Page data",
            type = "array",
            requiredMode = Schema.RequiredMode.REQUIRED,
            contentSchema = Object.class
    )
    private List<DataType> data;

    /**
     * Information about current and available pages
     */
    @NotNull
    @Schema(
            description = "Information about current and available pages",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private PageInfo page;

    /**
     * Information about navigation between pages
     */
    @NotNull
    @Schema(
            description = "Information about navigation between pages",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private PageLinks links;

    /**
     * Create response for given {@link Page}
     *
     * @param page page to return
     * @param request HTTP request
     */
    public PageableResponse(
            final @NotNull HttpServletRequest request,
            final @NotNull Page<DataType> page
    ) {
        this.data = page.getContent();
        this.page = new PageInfo(page.getSize(), page.getTotalElements(), page.getTotalPages(), page.getNumber());
        this.links = new PageLinks(
                self(request, page),
                next(request, page),
                prev(request, page),
                first(request, page),
                last(request, page)
        );
    }

    @NotNull
    private String self(final @NotNull HttpServletRequest request, final @NotNull Page<?> page) {
        final var requestURI = request.getRequestURI();
        final var requestParams = request.getParameterMap();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestURI);
        for (final var key : requestParams.keySet()) {
            builder.queryParam(key, Arrays.asList(requestParams.get(key)));
        }
        if (!requestParams.containsKey("page")) {
            builder.queryParam("page", List.of(page.getNumber()));
        }
        if (!requestParams.containsKey("limit")) {
            builder.queryParam("limit", List.of(page.getSize()));
        }

        return builder.build().toUriString();
    }

    @Nullable
    private String next(final @NotNull HttpServletRequest request, final @NotNull Page<?> page) {
        // next page doesn't exist
        if (page.getNumber() >= page.getTotalPages()) {
            return null;
        }

        final var requestURI = request.getRequestURI();
        final var requestParams = request.getParameterMap();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestURI);
        for (final var key : requestParams.keySet()) {
            builder.queryParam(key, Arrays.asList(requestParams.get(key)));
        }
        builder.replaceQueryParam("page", List.of(page.getNumber() + 1));
        builder.replaceQueryParam("limit", List.of(page.getSize()));

        return builder.build().toUriString();
    }

    @Nullable
    private String prev(final @NotNull HttpServletRequest request, final @NotNull Page<?> page) {
        // prev page doesn't exist
        if (page.getNumber() == 0) {
            return null;
        }

        final var requestURI = request.getRequestURI();
        final var requestParams = request.getParameterMap();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestURI);
        for (final var key : requestParams.keySet()) {
            builder.queryParam(key, Arrays.asList(requestParams.get(key)));
        }
        builder.replaceQueryParam("page", List.of(page.getNumber() - 1));
        builder.replaceQueryParam("limit", List.of(page.getSize()));

        return builder.build().toUriString();
    }

    @NotNull
    private String first(final @NotNull HttpServletRequest request, final @NotNull Page<?> page) {
        final var requestURI = request.getRequestURI();
        final var requestParams = request.getParameterMap();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestURI);
        for (final var key : requestParams.keySet()) {
            builder.queryParam(key, Arrays.asList(requestParams.get(key)));
        }
        builder.replaceQueryParam("page", List.of(0));
        builder.replaceQueryParam("limit", List.of(page.getSize()));

        return builder.build().toUriString();
    }

    @NotNull
    private String last(final @NotNull HttpServletRequest request, final @NotNull Page<?> page) {
        final var requestURI = request.getRequestURI();
        final var requestParams = request.getParameterMap();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(requestURI);
        for (final var key : requestParams.keySet()) {
            builder.queryParam(key, Arrays.asList(requestParams.get(key)));
        }
        builder.replaceQueryParam("page", List.of(page.getTotalPages()));
        builder.replaceQueryParam("limit", List.of(page.getSize()));

        return builder.build().toUriString();
    }

    /**
     * Page information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Page information")
    public static class PageInfo {
        @Schema(description = "Page size")
        private int size;
        
        @Schema(description = "Total number of elements")
        private long totalElements;
        
        @Schema(description = "Total number of pages")
        private int totalPages;
        
        @Schema(description = "Current page number")
        private int number;
    }

    /**
     * Page navigation links
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Page navigation links")
    public static class PageLinks {
        @Schema(description = "Current page link")
        private String self;
        
        @Schema(description = "Next page link")
        private String next;
        
        @Schema(description = "Previous page link")
        private String prev;
        
        @Schema(description = "First page link")
        private String first;
        
        @Schema(description = "Last page link")
        private String last;
    }
}