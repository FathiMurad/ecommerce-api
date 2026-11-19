package com.ecommerce.api.security;

import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Enforces ownership access rules on orders to prevent IDOR vulnerabilities.
 */
@Component
public class OrderAccessPolicy {

    public void assertCanAccess(Order order, User viewer) {
        // Guest orders have no associated user and rely on the tracking number as a bearer secret
        if (order.getUser() == null) {
            return;
        }

        boolean isOwner = viewer != null
                && viewer.getId() != null
                && viewer.getId().equals(order.getUser().getId());
        boolean isAdmin = viewer != null && viewer.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            // Return 404 instead of 403 to prevent leaking whether a tracking number exists
            throw new ResourceNotFoundException("Order not found with tracking number: " + order.getTrackingNumber());
        }
    }
}
