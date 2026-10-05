package com.ecommerce.api.exception;

/**
 * Thrown when an order checkout is attempted on an empty shopping cart.
 */
public class EmptyCartException extends RuntimeException {

    public EmptyCartException(String message) {
        super(message);
    }
}
