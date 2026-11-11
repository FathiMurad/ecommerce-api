package com.ecommerce.api.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic container for paginated and sorted API responses.
 */
public record PagedResponse<T>(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages,
                               boolean isLast) {

    public PagedResponse(List<T> content, Page<?> page) {
        this(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
