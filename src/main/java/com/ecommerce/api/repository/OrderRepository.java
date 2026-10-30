package com.ecommerce.api.repository;

import com.ecommerce.api.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByTrackingNumber(String trackingNumber);

    /**
     * Fetches an order alongside its line items and associated products in a single SQL query
     * to eliminate N+1 query overhead during order detail presentation.
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.trackingNumber = :trackingNumber")
    Optional<Order> findByTrackingNumberWithItems(@Param("trackingNumber") String trackingNumber);

    Page<Order> findByCustomerEmail(String customerEmail, Pageable pageable);

    Page<Order> findByUserId(Long userId, Pageable pageable);
}
