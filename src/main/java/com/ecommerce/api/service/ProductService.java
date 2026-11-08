package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.ProductRequest;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.dto.response.ProductResponse;

/**
 * Service for managing catalog products and inventory.
 */
public interface ProductService {

    PagedResponse<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection);

    ProductResponse getProductById(Long id);

    PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, int page, int size, String sortBy, String sortDirection);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
