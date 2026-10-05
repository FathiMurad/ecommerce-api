package com.ecommerce.api.service;

import com.ecommerce.api.dto.response.ProductResponse;

import java.util.List;

/**
 * Service contract for product catalog operations.
 */
public interface ProductService {

    /**
     * Retrieves all products in the catalog.
     *
     * @return List of ProductResponse DTOs.
     */
    List<ProductResponse> getAllProducts();

    /**
     * Retrieves a single product by its primary key alongside all associated images.
     *
     * @param id The product identifier.
     * @return The ProductResponse DTO.
     */
    ProductResponse getProductById(Long id);

    /**
     * Retrieves all products belonging to a given category.
     *
     * @param categoryId The category identifier.
     * @return List of ProductResponse DTOs.
     */
    List<ProductResponse> getProductsByCategoryId(Long categoryId);
}
