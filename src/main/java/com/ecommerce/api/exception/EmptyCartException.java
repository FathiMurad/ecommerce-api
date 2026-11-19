package com.ecommerce.api.exception;

/**
 * Thrown when checkout is attempted on an empty shopping cart, resulting in HTTP 400 Bad Request.
 */
public class EmptyCartException extends RuntimeException {

    public EmptyCartException(String message) {
        super(message);
    }
}
