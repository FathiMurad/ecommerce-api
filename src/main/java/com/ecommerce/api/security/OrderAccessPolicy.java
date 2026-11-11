package com.ecommerce.api.security;

import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Enforces resource ownership boundaries on orders to prevent IDOR attacks.
 * <p>
 * Guest orders are identified solely by their secure tracking number.
 * Registered user orders can only be accessed by the owner or system administrators.
 * Returns 404 (ResourceNotFoundException) instead of 403 to avoid existence oracle leaks.
 */
@Component
public class OrderAccessPolicy {

    public void assertCanAccess(Order order, User viewer) {
        if (order.getUser() == null) {
            // Guest order: tracking number is the bearer secret
            return;
        }

        boolean isOwner = viewer != null && viewer.getId() != null && viewer.getId().equals(order.getUser().getId());
        boolean isAdmin = viewer != null && viewer.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            throw new ResourceNotFoundException("Order not found with tracking number: " + order.getTrackingNumber());
        }
    }
}
