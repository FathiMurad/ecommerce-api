package com.ecommerce.api.exception;

import com.ecommerce.api.entity.Product;

/**
 * Thrown when an operation requests more inventory than currently available in stock.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public static InsufficientStockException of(Product product, long requested) {
        return new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'. Available: " + product.getStockQuantity() + ", Requested: " + requested);
    }
}
