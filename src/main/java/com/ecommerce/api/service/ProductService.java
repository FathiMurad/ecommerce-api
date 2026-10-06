package com.ecommerce.api.service;

import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.dto.response.ProductResponse;

/**
 * Service contract for product catalog operations.
 */
public interface ProductService {

    /**
     * Retrieves a paginated and sorted page of products.
     *
     * @param page          Zero-based page index.
     * @param size          Number of records per page.
     * @param sortBy        Field name to sort by.
     * @param sortDirection Sort direction ('asc' or 'desc').
     * @return PagedResponse containing ProductResponse items.
     */
    PagedResponse<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection);

    /**
     * Retrieves a single product by its primary key alongside all associated images.
     *
     * @param id The unique identifier of the product.
     * @return ProductResponse details.
     */
    ProductResponse getProductById(Long id);

    /**
     * Retrieves a paginated and sorted page of products belonging to a given category.
     *
     * @param categoryId    The category ID filter.
     * @param page          Zero-based page index.
     * @param size          Number of records per page.
     * @param sortBy        Field name to sort by.
     * @param sortDirection Sort direction ('asc' or 'desc').
     * @return PagedResponse containing filtered ProductResponse items.
     */
    PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, int page, int size, String sortBy, String sortDirection);

    /**
     * Deletes an existing product and its associations by primary key.
     *
     * @param id The unique identifier of the product to delete.
     */
    void deleteProduct(Long id);
}
