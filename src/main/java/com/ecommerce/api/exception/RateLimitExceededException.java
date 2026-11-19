package com.ecommerce.api.exception;

import lombok.Getter;

/**
 * Thrown when a client exceeds a rate-limiting threshold, resulting in HTTP 429 Too Many Requests.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
