package com.ecommerce.api.exception;

import com.ecommerce.api.entity.Product;

/**
 * Thrown when requested product quantity exceeds available stock, resulting in HTTP 409 Conflict.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public static InsufficientStockException of(Product product, long requested) {
        return new InsufficientStockException(
                "Insufficient stock for product '" + product.getName() + "'. Available: " + product.getStockQuantity() + ", Requested: " + requested
        );
    }
}
