package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.ProductRequest;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.dto.response.ProductResponse;
import com.ecommerce.api.util.PageRequests;
import org.springframework.data.domain.Pageable;

import java.util.Set;

/**
 * Service for managing catalog products and inventory.
 */
public interface ProductService {

    Set<String> ALLOWED_SORT_PROPERTIES = Set.of("id", "name", "price", "stockQuantity");

    PagedResponse<ProductResponse> getAllProducts(Pageable pageable);

    default PagedResponse<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection) {
        Pageable pageable = PageRequests.from(page, size, sortBy, sortDirection, ALLOWED_SORT_PROPERTIES, "id");
        return getAllProducts(pageable);
    }

    ProductResponse getProductById(Long id);

    PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, Pageable pageable);

    default PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, int page, int size, String sortBy, String sortDirection) {
        Pageable pageable = PageRequests.from(page, size, sortBy, sortDirection, ALLOWED_SORT_PROPERTIES, "id");
        return getProductsByCategoryId(categoryId, pageable);
    }

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);
}
