package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.CategoryRequest;
import com.ecommerce.api.dto.response.CategoryResponse;

import java.util.List;

/**
 * Service contract for category catalog operations.
 */
public interface CategoryService {

    /**
     * Retrieves all available categories.
     *
     * @return List of CategoryResponse DTOs.
     */
    List<CategoryResponse> getAllCategories();

    /**
     * Retrieves a single category by its primary key.
     *
     * @param id The category identifier.
     * @return The CategoryResponse DTO.
     */
    CategoryResponse getCategoryById(Long id);

    /**
     * Creates a new product category.
     *
     * @param request The category creation payload.
     * @return The created CategoryResponse DTO.
     */
    CategoryResponse createCategory(CategoryRequest request);

    /**
     * Updates an existing category by its identifier.
     *
     * @param id      The category identifier.
     * @param request The updated category details.
     * @return The updated CategoryResponse DTO.
     */
    CategoryResponse updateCategory(Long id, CategoryRequest request);

    /**
     * Deletes a category by its identifier.
     *
     * @param id The category identifier to delete.
     */
    void deleteCategory(Long id);
}
