package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.ProductRequest;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.dto.response.ProductResponse;
import org.springframework.data.domain.Pageable;

/**
 * Service for managing catalog products and inventory.
 */
public interface ProductService {

    PagedResponse<ProductResponse> getAllProducts(Pageable pageable);

    ProductResponse getProductById(Long id);

    PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, Pageable pageable);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
