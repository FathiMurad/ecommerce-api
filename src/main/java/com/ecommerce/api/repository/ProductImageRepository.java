package com.ecommerce.api.repository;

import com.ecommerce.api.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access repository for ProductImage operations, gallery ordering,
 * and primary thumbnail lookups.
 */
@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);

    Optional<ProductImage> findByProductIdAndIsPrimaryTrue(Long productId);

    Optional<ProductImage> findFirstByProductIdAndIsPrimaryTrue(Long productId);

    boolean existsByProductIdAndIsPrimaryTrue(Long productId);

    long countByProductId(Long productId);
}
