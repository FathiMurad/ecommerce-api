package com.ecommerce.api.security.ratelimit;

import com.ecommerce.api.exception.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Service managing bounded Caffeine token-bucket instances per client IP and endpoint type,
 * as well as per-email failed login lockouts.
 */
@Slf4j
@Service
public class RateLimiterService {

    private static final int MAX_LOGIN_FAILURES_PER_EMAIL = 5;
    private static final Duration EMAIL_LOCKOUT_DURATION = Duration.ofMinutes(15);

    private record LoginFailureState(int failures, Instant lockedUntil) {
    }

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).maximumSize(50_000).build();

    private final Cache<String, LoginFailureState> loginFailuresByEmail = Caffeine.newBuilder().expireAfterWrite(EMAIL_LOCKOUT_DURATION).maximumSize(50_000).build();

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

    @Value("${app.security.rate-limit.payment.capacity:20}")
    private long paymentCapacity;

    @Value("${app.security.rate-limit.payment.tokens-per-second:0.34}")
    private double paymentTokensPerSecond;

    @Value("${app.security.rate-limit.cart.capacity:60}")
    private long cartCapacity;

    @Value("${app.security.rate-limit.cart.tokens-per-second:1.0}")
    private double cartTokensPerSecond;

    public RateLimitResult tryAcquire(String clientIp, RateLimitType type) {
        if (!enabled) {
            return RateLimitResult.allowed(100);
        }

        String bucketKey = type.name() + ":" + clientIp;
        TokenBucket bucket = buckets.get(bucketKey, k -> createBucket(type));

        if (bucket.tryConsume()) {
            return RateLimitResult.allowed(bucket.getAvailableTokens());
        }

        long retryAfter = bucket.getSecondsUntilNextToken();
        log.warn("Rate limit exceeded for client IP [{}] on [{}] endpoint. Retry-After: {}s", clientIp, type, retryAfter);
        return RateLimitResult.denied(retryAfter);
    }

    public void checkEmailLoginAllowed(String normalizedEmail) {
        if (!enabled || normalizedEmail == null) {
            return;
        }

        LoginFailureState state = loginFailuresByEmail.getIfPresent(normalizedEmail);
        if (state != null && state.lockedUntil() != null) {
            long secondsRemaining = Duration.between(Instant.now(), state.lockedUntil()).getSeconds();
            if (secondsRemaining > 0) {
                log.warn("Login blocked by email lockout policy. Retry-After: {}s", secondsRemaining);
                throw new RateLimitExceededException("Rate limit exceeded due to repeated failed login attempts. Please retry after " + secondsRemaining + " seconds.", secondsRemaining);
            }
            loginFailuresByEmail.invalidate(normalizedEmail);
        }
    }

    public void recordFailedLogin(String normalizedEmail) {
        if (!enabled || normalizedEmail == null) {
            return;
        }

        loginFailuresByEmail.asMap().compute(normalizedEmail, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || (existing.lockedUntil() != null && !now.isBefore(existing.lockedUntil()))) {
                return new LoginFailureState(1, null);
            }
            int count = existing.failures() + 1;
            Instant lockUntil = count >= MAX_LOGIN_FAILURES_PER_EMAIL ? (existing.lockedUntil() != null ? existing.lockedUntil() : now.plus(EMAIL_LOCKOUT_DURATION)) : null;
            return new LoginFailureState(count, lockUntil);
        });
    }

    public void clearFailedLogins(String normalizedEmail) {
        if (normalizedEmail != null) {
            loginFailuresByEmail.invalidate(normalizedEmail);
        }
    }

    public void clear() {
        buckets.invalidateAll();
        loginFailuresByEmail.invalidateAll();
    }

    private TokenBucket createBucket(RateLimitType type) {
        return switch (type) {
            case AUTH -> new TokenBucket(authCapacity, authTokensPerSecond);
            case CHECKOUT -> new TokenBucket(checkoutCapacity, checkoutTokensPerSecond);
            case PAYMENT -> new TokenBucket(paymentCapacity, paymentTokensPerSecond);
            case CART -> new TokenBucket(cartCapacity, cartTokensPerSecond);
        };
    }
}
