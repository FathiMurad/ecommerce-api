package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for loading shopping carts and their items.
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Loads a guest cart with its items and products in one query to avoid N+1 issues.
     */
    @Query("SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.cartToken = :cartToken")
    Optional<Cart> findByCartTokenWithItems(@Param("cartToken") String cartToken);

    /**
     * Loads a user cart with its items and products in one query to avoid N+1 issues.
     */
    @Query("SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);
}
