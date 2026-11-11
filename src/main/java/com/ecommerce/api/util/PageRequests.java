package com.ecommerce.api.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

public final class PageRequests {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable safe(Pageable in, Set<String> allowedSortProperties, String defaultSortProperty) {
        if (in == null) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, defaultSortProperty));
        }

        List<Sort.Order> safeOrders = in.getSort().stream().filter(order -> allowedSortProperties.contains(order.getProperty())).toList();

        Sort safeSort = safeOrders.isEmpty() ? Sort.by(Sort.Direction.DESC, defaultSortProperty) : Sort.by(safeOrders);

        int size = in.getPageSize() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(in.getPageSize(), MAX_PAGE_SIZE);
        int page = Math.max(in.getPageNumber(), 0);

        return PageRequest.of(page, size, safeSort);
    }

    public static Pageable from(int page, int size, String sortBy, String sortDirection, Set<String> allowedSortProperties, String defaultSortProperty) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        String property = (sortBy != null && allowedSortProperties.contains(sortBy.trim())) ? sortBy.trim() : defaultSortProperty;

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return PageRequest.of(safePage, safeSize, Sort.by(direction, property));
    }
}
