package com.ecommerce.api.dto.response;

import java.util.List;

/**
 * Generic container for paginated and sorted API responses.
 */
public record PagedResponse<T>(List<T> content, int pageNumber, int pageSize, long totalElements, int totalPages,
                               boolean isLast) {
}
