package com.ecommerce.api.exception;

/**
 * Thrown when an entity requested by a client does not exist in the database.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
