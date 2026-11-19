package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Category;
import com.ecommerce.api.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for managing {@link Category} entities and validating category uniqueness.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    default Category getOrThrow(Long id) {
        return findById(id).orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }
}
