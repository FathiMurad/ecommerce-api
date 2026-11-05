package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.CategoryRequest;
import com.ecommerce.api.dto.response.CategoryResponse;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.exception.ConflictException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.ProductRepository;
import com.ecommerce.api.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for managing product categories.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Cacheable(value = "categories", key = "'all'")
    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        List<CategoryResponse> responses = new ArrayList<>();

        for (Category category : categories) {
            responses.add(CategoryResponse.fromEntity(category));
        }

        return responses;
    }

    @Override
    @Cacheable(value = "categories", key = "#id")
    public CategoryResponse getCategoryById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Category ID cannot be null.");
        }

        Category category = findCategoryOrThrow(id);

        return CategoryResponse.fromEntity(category);
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required.");
        }

        String name = request.getName().trim();
        if (categoryRepository.existsByName(name)) {
            log.warn("Category creation rejected: Name '{}' already exists", name);
            throw new ConflictException("Category with name '" + name + "' already exists.");
        }

        Category category = Category.builder().name(name).description(request.getDescription()).build();

        Category savedCategory = categoryRepository.save(category);
        log.info("Created category id={} with name='{}'", savedCategory.getId(), savedCategory.getName());

        return CategoryResponse.fromEntity(savedCategory);
    }

    @Override
    @Transactional
    @Caching(evict = {@CacheEvict(value = "categories", allEntries = true), @CacheEvict(value = "products", allEntries = true)})
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("Category ID cannot be null.");
        }
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required.");
        }

        Category category = findCategoryOrThrow(id);

        String name = request.getName().trim();
        Optional<Category> existingCategory = categoryRepository.findByName(name);
        if (existingCategory.isPresent() && !existingCategory.get().getId().equals(id)) {
            log.warn("Category update rejected: Name '{}' already exists", name);
            throw new ConflictException("Category with name '" + name + "' already exists.");
        }

        category.setName(name);
        category.setDescription(request.getDescription());

        Category updatedCategory = categoryRepository.save(category);
        log.info("Updated category id={} with name='{}'", updatedCategory.getId(), updatedCategory.getName());

        return CategoryResponse.fromEntity(updatedCategory);
    }

    @Override
    @Transactional
    @Caching(evict = {@CacheEvict(value = "categories", allEntries = true), @CacheEvict(value = "products", allEntries = true)})
    public void deleteCategory(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Category ID cannot be null.");
        }

        Category category = findCategoryOrThrow(id);

        if (productRepository.existsByCategoryId(id)) {
            throw new BadRequestException("Cannot delete category '" + category.getName() + "' because it contains associated products. Reassign or delete products first.");
        }

        categoryRepository.delete(category);
        log.info("Deleted category id={} with name='{}'", id, category.getName());
    }

    private Category findCategoryOrThrow(Long id) {
        Optional<Category> category = categoryRepository.findById(id);
        if (category.isEmpty()) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }

        return category.get();
    }
}
