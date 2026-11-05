package com.ecommerce.api.exception;

/**
 * Exception thrown when a client request violates business logic or state invariants.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
