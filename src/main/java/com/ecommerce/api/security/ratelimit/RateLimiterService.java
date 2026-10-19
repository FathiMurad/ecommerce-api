package com.ecommerce.api.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing token-bucket instances per client IP and endpoint type.
 */
@Slf4j
@Service
public class RateLimiterService {

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.auth.capacity:20}")
    private long authCapacity;

    @Value("${app.security.rate-limit.auth.tokens-per-second:0.34}")
    private double authTokensPerSecond;

    @Value("${app.security.rate-limit.checkout.capacity:20}")
    private long checkoutCapacity;

    @Value("${app.security.rate-limit.checkout.tokens-per-second:0.34}")
    private double checkoutTokensPerSecond;

    public RateLimitResult tryAcquire(String clientIp, RateLimitType type) {
        if (!enabled) {
            return RateLimitResult.allowed(100);
        }

        String bucketKey = type.name() + ":" + clientIp;
        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(type));

        if (bucket.tryConsume()) {
            return RateLimitResult.allowed(bucket.getAvailableTokens());
        }

        long retryAfter = bucket.getSecondsUntilNextToken();
        log.warn("Rate limit exceeded for client IP [{}] on [{}] endpoint. Retry-After: {}s", clientIp, type, retryAfter);
        return RateLimitResult.denied(retryAfter);
    }

    public void clear() {
        buckets.clear();
    }

    private TokenBucket createBucket(RateLimitType type) {
        if (type == RateLimitType.AUTH) {
            return new TokenBucket(authCapacity, authTokensPerSecond);
        } else if (type == RateLimitType.CHECKOUT) {
            return new TokenBucket(checkoutCapacity, checkoutTokensPerSecond);
        }
        return new TokenBucket(20, 0.34);
    }
}
