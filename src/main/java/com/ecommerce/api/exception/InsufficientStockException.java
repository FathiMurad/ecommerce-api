package com.ecommerce.api.exception;

/**
 * Thrown when an operation requests more inventory than currently available in stock.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
