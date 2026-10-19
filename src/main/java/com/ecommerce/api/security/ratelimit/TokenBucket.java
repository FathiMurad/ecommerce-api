package com.ecommerce.api.security.ratelimit;

/**
 * Thread-safe Token Bucket implementation for rate limiting.
 */
public class TokenBucket {

    private final long capacity;
    private final double refillTokensPerSecond;
    private double availableTokens;
    private long lastRefillTimestampNanos;

    public TokenBucket(long capacity, double refillTokensPerSecond) {
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.availableTokens = capacity;
        this.lastRefillTimestampNanos = System.nanoTime();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return true;
        }
        return false;
    }

    public synchronized long getSecondsUntilNextToken() {
        refill();
        if (availableTokens >= 1.0) {
            return 0;
        }
        double missing = 1.0 - availableTokens;
        return Math.max(1, (long) Math.ceil(missing / refillTokensPerSecond));
    }

    public synchronized long getAvailableTokens() {
        refill();
        return (long) Math.floor(availableTokens);
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillTimestampNanos;
        if (elapsedNanos > 0) {
            double tokensToAdd = (elapsedNanos / 1_000_000_000.0) * refillTokensPerSecond;
            availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
            lastRefillTimestampNanos = now;
        }
    }
}
