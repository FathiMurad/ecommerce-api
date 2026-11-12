package com.ecommerce.api.repository;

import com.ecommerce.api.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for querying historical order line items.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByProductId(Long productId);
}
