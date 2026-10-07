package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.CategoryRequest;
import com.ecommerce.api.dto.response.CategoryResponse;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of CategoryService providing transactional catalog category management.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        List<CategoryResponse> responses = new ArrayList<>();

        for (Category category : categories) {
            responses.add(CategoryResponse.fromEntity(category));
        }

        return responses;
    }

    @Override
    public CategoryResponse getCategoryById(Long id) {
        Optional<Category> categoryOptional = categoryRepository.findById(id);

        if (categoryOptional.isEmpty()) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }

        return CategoryResponse.fromEntity(categoryOptional.get());
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = Category.builder().name(request.getName().trim()).description(request.getDescription()).build();

        Category savedCategory = categoryRepository.save(category);
        return CategoryResponse.fromEntity(savedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());

        Category updatedCategory = categoryRepository.save(category);
        return CategoryResponse.fromEntity(updatedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        categoryRepository.delete(category);
    }
}
