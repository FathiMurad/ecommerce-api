package com.ecommerce.api.repository;

import com.ecommerce.api.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for querying {@link OrderItem} entities and verifying product purchase references.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByProductId(Long productId);
}
