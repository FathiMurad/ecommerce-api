package com.ecommerce.api.service.impl;

import com.ecommerce.api.constant.CacheNames;
import com.ecommerce.api.dto.request.CategoryRequest;
import com.ecommerce.api.dto.response.CategoryResponse;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.exception.ConflictException;
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

import java.util.List;
import java.util.Objects;
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
    @Cacheable(value = CacheNames.CATEGORIES, key = "'all'")
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().map(CategoryResponse::fromEntity).toList();
    }

    @Override
    @Cacheable(value = CacheNames.CATEGORIES, key = "#id")
    public CategoryResponse getCategoryById(Long id) {
        Objects.requireNonNull(id, "Category ID cannot be null.");
        Category category = categoryRepository.getOrThrow(id);
        return CategoryResponse.fromEntity(category);
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORIES, allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        Objects.requireNonNull(request, "Category request cannot be null.");

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
    @Caching(evict = {@CacheEvict(value = CacheNames.CATEGORIES, allEntries = true), @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)})
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Objects.requireNonNull(id, "Category ID cannot be null.");
        Objects.requireNonNull(request, "Category request cannot be null.");

        Category category = categoryRepository.getOrThrow(id);

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
    @CacheEvict(value = CacheNames.CATEGORIES, allEntries = true)
    public void deleteCategory(Long id) {
        Objects.requireNonNull(id, "Category ID cannot be null.");

        Category category = categoryRepository.getOrThrow(id);

        if (productRepository.existsByCategoryId(id)) {
            log.warn("Category deletion rejected: Category id={} contains associated products", id);
            throw new BadRequestException("Cannot delete category '" + category.getName() + "' because it contains associated products. Reassign or delete products first.");
        }

        categoryRepository.delete(category);
        log.info("Deleted category id={} with name='{}'", id, category.getName());
    }
}
