package com.ecommerce.api.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Generic response payload wrapping paginated and sorted API results.
 */
public record PagedResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {

    public static <E, R> PagedResponse<R> from(Page<E> page, Function<? super E, ? extends R> mapper) {
        Page<R> mappedPage = page.map(mapper);
        return new PagedResponse<>(
                mappedPage.getContent(),
                mappedPage.getNumber(),
                mappedPage.getSize(),
                mappedPage.getTotalElements(),
                mappedPage.getTotalPages(),
                mappedPage.isLast()
        );
    }
}
