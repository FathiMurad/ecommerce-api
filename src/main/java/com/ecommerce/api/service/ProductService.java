package com.ecommerce.api.service;

import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.dto.response.ProductResponse;

/**
 * Service contract for product catalog operations.
 */
public interface ProductService {

    /**
     * Retrieves a paginated and sorted page of products.
     */
    PagedResponse<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection);

    /**
     * Retrieves a single product by its primary key alongside all associated images.
     */
    ProductResponse getProductById(Long id);

    /**
     * Retrieves a paginated and sorted page of products belonging to a given category.
     */
    PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, int page, int size, String sortBy, String sortDirection);
}
