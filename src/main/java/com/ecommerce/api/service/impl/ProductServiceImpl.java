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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of ProductService providing transactional catalog product operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public PagedResponse<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDirection) {
        Sort sort = sortDirection.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Product> productPage = productRepository.findAll(pageable);

        List<ProductResponse> content = new ArrayList<>();
        for (Product product : productPage.getContent()) {
            content.add(ProductResponse.fromEntity(product));
        }

        return new PagedResponse<>(content, productPage.getNumber(), productPage.getSize(), productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast());
    }

    @Override
    public ProductResponse getProductById(Long id) {
        Optional<Product> productOptional = productRepository.findByIdWithImages(id);

        if (productOptional.isEmpty()) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }

        return ProductResponse.fromEntity(productOptional.get());
    }

    @Override
    public PagedResponse<ProductResponse> getProductsByCategoryId(Long categoryId, int page, int size, String sortBy, String sortDirection) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }

        Sort sort = sortDirection.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Product> productPage = productRepository.findByCategoryId(categoryId, pageable);

        List<ProductResponse> content = new ArrayList<>();
        for (Product product : productPage.getContent()) {
            content.add(ProductResponse.fromEntity(product));
        }

        return new PagedResponse<>(content, productPage.getNumber(), productPage.getSize(), productPage.getTotalElements(), productPage.getTotalPages(), productPage.isLast());
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        Product product = Product.builder().name(request.getName().trim()).description(request.getDescription()).price(request.getPrice()).stockQuantity(request.getStockQuantity()).category(category).build();

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                product.addImage(ProductImage.builder().imageUrl(request.getImageUrls().get(i)).isPrimary(i == 0).displayOrder(i).build());
            }
        }

        Product savedProduct = productRepository.save(product);
        return ProductResponse.fromEntity(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()));

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(category);

        Product updatedProduct = productRepository.save(product);
        return ProductResponse.fromEntity(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        productRepository.delete(product);
    }
}
