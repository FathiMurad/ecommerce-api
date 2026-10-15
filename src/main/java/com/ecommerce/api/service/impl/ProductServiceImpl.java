package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.ProductRequest;
import com.ecommerce.api.dto.response.PagedResponse;
import com.ecommerce.api.dto.response.ProductResponse;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.ProductImage;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Implementation of ProductService providing transactional catalog product operations,
 * safe pagination sanitization, and synchronized image management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("id", "name", "price", "stockQuantity");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public PagedResponse<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection) {
        Pageable pageable = createSafePageable(page, size, sortBy, sortDirection);
        Page<Product> productPage = productRepository.findAll(pageable);

        List<ProductResponse> content = productPage.getContent().stream().map(ProductResponse::fromEntity).toList();

        return new PagedResponse<>(content, productPage.getNumber(), productPage.getSize(), productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast());
    }

    @Override
    public ProductResponse getProductById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        Product product = productRepository.findByIdWithImages(id).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        return ProductResponse.fromEntity(product);
    }

    @Override
    public PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, int page, int size, String sortBy, String sortDirection) {
        if (categoryId == null) {
            throw new IllegalArgumentException("Category ID cannot be null.");
        }
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }

        Pageable pageable = createSafePageable(page, size, sortBy, sortDirection);
        Page<Product> productPage = productRepository.findByCategoryId(categoryId, pageable);

        List<ProductResponse> content = productPage.getContent().stream().map(ProductResponse::fromEntity).toList();

        return new PagedResponse<>(content, productPage.getNumber(), productPage.getSize(), productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast());
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Product request cannot be null.");
        }
        if (request.getCategoryId() == null) {
            throw new IllegalArgumentException("Category ID cannot be null.");
        }

        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        Product product = Product.builder().name(request.getName() != null ? request.getName().trim() : null).description(request.getDescription()).price(request.getPrice()).stockQuantity(request.getStockQuantity()).category(category).build();

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                String imageUrl = request.getImageUrls().get(i);
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    product.addImage(ProductImage.builder().imageUrl(imageUrl.trim()).isPrimary(i == 0).displayOrder(i).build());
                }
            }
        }

        Product savedProduct = productRepository.save(product);
        log.info("Created product id={} with name='{}'", savedProduct.getId(), savedProduct.getName());

        return ProductResponse.fromEntity(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Product request cannot be null.");
        }
        if (request.getCategoryId() == null) {
            throw new IllegalArgumentException("Category ID cannot be null.");
        }

        Product product = productRepository.findByIdWithImages(id).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        product.setName(request.getName() != null ? request.getName().trim() : product.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(category);

        if (request.getImageUrls() != null) {
            product.getImages().clear();
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                String imageUrl = request.getImageUrls().get(i);
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    product.addImage(ProductImage.builder().imageUrl(imageUrl.trim()).isPrimary(i == 0).displayOrder(i).build());
                }
            }
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Updated product id={} with name='{}'", updatedProduct.getId(), updatedProduct.getName());

        return ProductResponse.fromEntity(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        Product product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        productRepository.delete(product);
        log.info("Deleted product id={} with name='{}'", id, product.getName());
    }

    /**
     * Sanitizes pagination parameters and sort properties against an allowed whitelist
     * to eliminate SQL sort injection and DoS memory exhaustion risks.
     */
    private Pageable createSafePageable(int page, int size, String sortBy, String sortDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        String property = (sortBy != null && ALLOWED_SORT_PROPERTIES.contains(sortBy.trim())) ? sortBy.trim() : "id";

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return PageRequest.of(safePage, safeSize, Sort.by(direction, property));
    }
}
