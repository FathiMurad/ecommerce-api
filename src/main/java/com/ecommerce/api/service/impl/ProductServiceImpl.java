package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.response.ProductResponse;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.ProductService;
import lombok.RequiredArgsConstructor;
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
    public List<ProductResponse> getAllProducts() {
        List<Product> products = productRepository.findAll();
        List<ProductResponse> responses = new ArrayList<>();

        for (Product product : products) {
            responses.add(ProductResponse.fromEntity(product));
        }

        return responses;
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
    public List<ProductResponse> getProductsByCategoryId(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Category not found with id: " + categoryId);
        }

        List<Product> products = productRepository.findByCategoryId(categoryId);
        List<ProductResponse> responses = new ArrayList<>();

        for (Product product : products) {
            responses.add(ProductResponse.fromEntity(product));
        }

        return responses;
    }
}
