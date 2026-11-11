package com.ecommerce.api.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Generic container for paginated and sorted API responses.
 */
public record PagedResponse<T>(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages,
                               boolean isLast) {

    public PagedResponse(List<T> content, Page<?> page) {
        this(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public static <E, R> PagedResponse<R> from(Page<E> page, Function<? super E, ? extends R> mapper) {
        Page<R> mappedPage = page.map(mapper);
        return new PagedResponse<>(mappedPage.getContent(), mappedPage.getNumber(), mappedPage.getSize(), mappedPage.getTotalElements(), mappedPage.getTotalPages(), mappedPage.isLast());
    }
}
