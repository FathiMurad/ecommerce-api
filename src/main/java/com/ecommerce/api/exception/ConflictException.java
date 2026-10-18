package com.ecommerce.api.exception;

/**
 * Thrown when an incoming request conflicts with the current server state
 * (e.g., duplicate registration email, concurrent collisions).
 * Translates to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
