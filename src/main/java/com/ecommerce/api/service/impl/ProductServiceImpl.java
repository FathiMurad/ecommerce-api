package com.ecommerce.api.service.impl;

import com.ecommerce.api.constant.CacheNames;
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
import com.ecommerce.api.util.PageRequests;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service implementation for managing catalog products and inventory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final int PRIMARY_IMAGE_INDEX = 0;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public PagedResponse<ProductResponse> getAllProducts(Pageable pageable) {
        Pageable safePageable = PageRequests.safe(pageable, ALLOWED_SORT_PROPERTIES, "id");
        Page<Product> productPage = productRepository.findAll(safePageable);
        return PagedResponse.from(productPage, ProductResponse::fromEntity);
    }

    @Override
    public PagedResponse<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection) {
        Pageable pageable = PageRequests.from(page, size, sortBy, sortDirection, ALLOWED_SORT_PROPERTIES, "id");
        return getAllProducts(pageable);
    }

    @Override
    @Cacheable(value = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse getProductById(Long id) {
        Objects.requireNonNull(id, "Product ID cannot be null.");
        Product product = productRepository.getWithImagesOrThrow(id);
        return ProductResponse.fromEntity(product);
    }

    @Override
    public PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, Pageable pageable) {
        Objects.requireNonNull(categoryId, "Category ID cannot be null.");
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }

        Pageable safePageable = PageRequests.safe(pageable, ALLOWED_SORT_PROPERTIES, "id");
        Page<Product> productPage = productRepository.findByCategoryId(categoryId, safePageable);
        return PagedResponse.from(productPage, ProductResponse::fromEntity);
    }

    @Override
    public PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, int page, int size, String sortBy, String sortDirection) {
        Pageable pageable = PageRequests.from(page, size, sortBy, sortDirection, ALLOWED_SORT_PROPERTIES, "id");
        return getProductsByCategoryId(categoryId, pageable);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Objects.requireNonNull(request, "Product request cannot be null.");

        Category category = categoryRepository.getOrThrow(request.getCategoryId());

        Product product = Product.builder().name(request.getName().trim()).description(request.getDescription()).price(request.getPrice()).stockQuantity(request.getStockQuantity()).category(category).build();

        attachProductImages(product, request.getImageUrls());

        Product savedProduct = productRepository.save(product);
        log.info("Created product id={} with name='{}'", savedProduct.getId(), savedProduct.getName());

        return ProductResponse.fromEntity(savedProduct);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCTS, key = "#id")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Objects.requireNonNull(id, "Product ID cannot be null.");
        Objects.requireNonNull(request, "Product request cannot be null.");

        Product product = productRepository.getWithImagesOrThrow(id);
        Category category = categoryRepository.getOrThrow(request.getCategoryId());

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(category);

        if (request.getImageUrls() != null) {
            product.getImages().clear();
            attachProductImages(product, request.getImageUrls());
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Updated product id={} with name='{}'", updatedProduct.getId(), updatedProduct.getName());

        return ProductResponse.fromEntity(updatedProduct);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCTS, key = "#id")
    public void deleteProduct(Long id) {
        Objects.requireNonNull(id, "Product ID cannot be null.");

        Product product = productRepository.getOrThrow(id);
        productRepository.delete(product);
        log.info("Deleted product id={} with name='{}'", id, product.getName());
    }

    private void attachProductImages(Product product, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        List<String> cleanUrls = new ArrayList<>();
        for (String url : imageUrls) {
            if (url != null && !url.trim().isEmpty()) {
                cleanUrls.add(url.trim());
            }
        }

        for (int i = 0; i < cleanUrls.size(); i++) {
            boolean isPrimary = (i == PRIMARY_IMAGE_INDEX);
            ProductImage image = ProductImage.builder().imageUrl(cleanUrls.get(i)).isPrimary(isPrimary).displayOrder(i).build();
            product.addImage(image);
        }
    }
}
