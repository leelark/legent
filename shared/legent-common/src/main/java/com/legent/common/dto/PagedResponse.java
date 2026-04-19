package com.legent.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Standard paginated response wrapper.
 * Used by all list/search endpoints.
 *
 * @param <T> the type of elements in the page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResponse<T> {

    private boolean success;
    private List<T> data;
    private Pagination pagination;
    private ApiResponse.Meta meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    /**
     * Creates a paged response from a Spring Data Page.
     */
    public static <T> PagedResponse<T> of(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        return PagedResponse.<T>builder()
                .success(true)
                .data(content)
                .pagination(Pagination.builder()
                        .page(page)
                        .size(size)
                        .totalElements(totalElements)
                        .totalPages(totalPages)
                        .build())
                .meta(ApiResponse.Meta.now())
                .build();
    }
}
