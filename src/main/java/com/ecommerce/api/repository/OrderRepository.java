package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for managing customer orders and history.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByTrackingNumber(String trackingNumber);

    /**
     * Loads an order with its items and products in one query to avoid N+1 issues.
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.trackingNumber = :trackingNumber")
    Optional<Order> findByTrackingNumberWithItems(@Param("trackingNumber") String trackingNumber);

    Page<Order> findByCustomerEmail(String customerEmail, Pageable pageable);

    Page<Order> findByUserId(Long userId, Pageable pageable);
}
