package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Eagerly fetches an anonymous guest cart along with its items and products
     * in a single query to eliminate N+1 overhead during cart viewing and calculation.
     */
    @Query("SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.cartToken = :cartToken")
    Optional<Cart> findByCartTokenWithItems(@Param("cartToken") String cartToken);

    /**
     * Eagerly fetches an authenticated user's cart along with its items and products
     * in a single query to eliminate N+1 overhead during cart viewing and calculation.
     */
    @Query("SELECT DISTINCT c FROM Cart c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);
}
