package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Cart;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing {@link Cart} entities and eager-loading cart line items.
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Loads a guest cart with its items and products in a single query to avoid N+1 issues.
     */
    @Query("SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.cartToken = :cartToken")
    Optional<Cart> findByCartTokenWithItems(@Param("cartToken") String cartToken);

    /**
     * Loads a user cart with its items and products in a single query to avoid N+1 issues.
     */
    @Query("SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);

    Page<Cart> findByUserIsNullAndUpdatedAtBefore(LocalDateTime cutoff, Pageable pageable);
}
