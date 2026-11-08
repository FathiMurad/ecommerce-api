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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service implementation for managing catalog products and inventory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("id", "name", "price", "stockQuantity");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PRIMARY_IMAGE_INDEX = 0;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public PagedResponse<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection) {
        Pageable pageable = createSafePageable(page, size, sortBy, sortDirection);
        Page<Product> productPage = productRepository.findAll(pageable);

        return mapToPagedResponse(productPage);
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }

        Product product = findProductWithImagesOrThrow(id);

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

        return mapToPagedResponse(productPage);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        validateProductRequest(request);

        Category category = findCategoryByIdOrThrow(request.getCategoryId());

        Product product = Product.builder().name(request.getName() != null ? request.getName().trim() : null).description(request.getDescription()).price(request.getPrice()).stockQuantity(request.getStockQuantity()).category(category).build();

        attachProductImages(product, request.getImageUrls());

        Product savedProduct = productRepository.save(product);
        log.info("Created product id={} with name='{}'", savedProduct.getId(), savedProduct.getName());

        return ProductResponse.fromEntity(savedProduct);
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }
        validateProductRequest(request);

        Product product = findProductWithImagesOrThrow(id);
        Category category = findCategoryByIdOrThrow(request.getCategoryId());

        product.setName(request.getName() != null ? request.getName().trim() : product.getName());
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
    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID cannot be null.");
        }

        Product product = findProductByIdOrThrow(id);

        productRepository.delete(product);
        log.info("Deleted product id={} with name='{}'", id, product.getName());
    }

    private void validateProductRequest(ProductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Product request cannot be null.");
        }
        if (request.getCategoryId() == null) {
            throw new IllegalArgumentException("Category ID cannot be null.");
        }
    }

    private PagedResponse<ProductResponse> mapToPagedResponse(Page<Product> productPage) {
        List<ProductResponse> content = new ArrayList<>();
        for (Product product : productPage.getContent()) {
            content.add(ProductResponse.fromEntity(product));
        }

        return new PagedResponse<>(content, productPage.getNumber(), productPage.getSize(), productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast());
    }

    private void attachProductImages(Product product, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        for (int i = 0; i < imageUrls.size(); i++) {
            String imageUrl = imageUrls.get(i);
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                boolean isPrimary = (i == PRIMARY_IMAGE_INDEX);

                ProductImage image = ProductImage.builder().imageUrl(imageUrl.trim()).isPrimary(isPrimary).displayOrder(i).build();

                product.addImage(image);
            }
        }
    }

    private Pageable createSafePageable(int page, int size, String sortBy, String sortDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        String property = (sortBy != null && ALLOWED_SORT_PROPERTIES.contains(sortBy.trim())) ? sortBy.trim() : "id";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;

        return PageRequest.of(safePage, safeSize, Sort.by(direction, property));
    }

    private Product findProductWithImagesOrThrow(Long id) {
        Optional<Product> product = productRepository.findByIdWithImages(id);
        if (product.isEmpty()) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }

        return product.get();
    }

    private Product findProductByIdOrThrow(Long id) {
        Optional<Product> product = productRepository.findById(id);
        if (product.isEmpty()) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }

        return product.get();
    }

    private Category findCategoryByIdOrThrow(Long categoryId) {
        Optional<Category> category = categoryRepository.findById(categoryId);
        if (category.isEmpty()) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }

        return category.get();
    }
}
