package com.ecommerce.api.exception;

/**
 * Thrown when a request conflicts with the current server state, resulting in HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
