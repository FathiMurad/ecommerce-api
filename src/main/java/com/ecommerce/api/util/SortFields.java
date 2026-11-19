package com.ecommerce.api.util;

import java.util.Set;

/**
 * Defines allowed entity sort fields for paginated queries to prevent invalid property lookups.
 */
public final class SortFields {

    public static final Set<String> PRODUCT_SORT_FIELDS = Set.of("id", "name", "price", "stockQuantity");
    public static final Set<String> ORDER_SORT_FIELDS = Set.of("id", "createdAt", "totalAmount", "status");

    private SortFields() {
    }
}
