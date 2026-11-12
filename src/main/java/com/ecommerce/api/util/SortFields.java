package com.ecommerce.api.util;

import java.util.Set;

/**
 * Central whitelist definitions for sortable entity properties across paginated endpoints.
 */
public final class SortFields {

    public static final Set<String> PRODUCT_SORT_FIELDS = Set.of("id", "name", "price", "stockQuantity");
    public static final Set<String> ORDER_SORT_FIELDS = Set.of("id", "createdAt", "totalAmount", "status");

    private SortFields() {
    }
}
