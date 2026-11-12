package com.ecommerce.api.exception;

/**
 * Thrown when a request is syntactically valid but violates a domain business rule (HTTP 422).
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
