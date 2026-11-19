package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Product;
import com.ecommerce.api.exception.ResourceNotFoundException;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for managing {@link Product} entities, catalog queries, and atomic stock updates.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Override
    @NonNull
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Loads a product with its images in a single query to avoid N+1 issues.
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.images WHERE p.id = :id")
    Optional<Product> findByIdWithImages(@Param("id") Long id);

    /**
     * Atomically decrements stock only if sufficient inventory is available.
     * Deliberately omits clearAutomatically so active Cart entities remain managed.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty, p.version = p.version + 1 WHERE p.id = :id AND p.stockQuantity >= :qty")
    int tryDecrementStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Atomically restores stock when an order is cancelled.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty, p.version = p.version + 1 WHERE p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);

    default Product getOrThrow(Long id) {
        return findById(id).orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    default Product getWithImagesOrThrow(Long id) {
        return findByIdWithImages(id).orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }
}
