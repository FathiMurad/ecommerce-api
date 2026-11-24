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
 * Manages bounded token buckets per client IP and endpoint type,
 * along with per-email/IP failed login lockouts against brute-force attempts.
 */
@Slf4j
@Service
public class RateLimiterService {

    private static final int MAX_LOGIN_FAILURES_PER_EMAIL_IP = 5;
    private static final Duration EMAIL_IP_LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final Duration BUCKET_IDLE_EXPIRATION = Duration.ofMinutes(10);
    private static final long MAX_CACHE_ENTRIES = 50_000L;
    private static final long DISABLED_REMAINING_TOKENS = 100L;

    private record LoginFailureState(int failures, Instant lockedUntil) {
    }

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(BUCKET_IDLE_EXPIRATION)
            .maximumSize(MAX_CACHE_ENTRIES)
            .build();

    private final Cache<String, LoginFailureState> loginFailuresByEmailIp = Caffeine.newBuilder()
            .expireAfterWrite(EMAIL_IP_LOCKOUT_DURATION)
            .maximumSize(MAX_CACHE_ENTRIES)
            .build();

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
            return RateLimitResult.allowed(DISABLED_REMAINING_TOKENS);
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

    public void checkEmailLoginAllowed(String normalizedEmail, String clientIp) {
        if (!enabled || normalizedEmail == null || clientIp == null) {
            return;
        }

        String stateKey = normalizedEmail + ":" + clientIp;
        LoginFailureState state = loginFailuresByEmailIp.getIfPresent(stateKey);
        if (state != null && state.lockedUntil() != null) {
            long secondsRemaining = Duration.between(Instant.now(), state.lockedUntil()).getSeconds();
            if (secondsRemaining > 0) {
                log.warn(
                        "Login blocked by email/IP lockout policy for email={} from IP={}. Retry-After: {}s",
                        normalizedEmail,
                        clientIp,
                        secondsRemaining
                );
                throw new RateLimitExceededException(
                        "Rate limit exceeded due to repeated failed login attempts. Please retry after "
                                + secondsRemaining + " seconds.",
                        secondsRemaining
                );
            }
            loginFailuresByEmailIp.invalidate(stateKey);
        }
    }

    public void recordFailedLogin(String normalizedEmail, String clientIp) {
        if (!enabled || normalizedEmail == null || clientIp == null) {
            return;
        }

        String stateKey = normalizedEmail + ":" + clientIp;
        // Atomic compute ensures thread-safe counter increments under concurrent login attempts
        loginFailuresByEmailIp.asMap().compute(stateKey, (key, existing) -> nextFailureState(existing));
    }

    private LoginFailureState nextFailureState(LoginFailureState existing) {
        Instant now = Instant.now();
        if (existing == null || isLockExpired(existing, now)) {
            return new LoginFailureState(1, null);
        }

        int newFailures = existing.failures() + 1;
        Instant lockUntil = (newFailures >= MAX_LOGIN_FAILURES_PER_EMAIL_IP)
                ? now.plus(EMAIL_IP_LOCKOUT_DURATION)
                : null;
        return new LoginFailureState(newFailures, lockUntil);
    }

    private boolean isLockExpired(LoginFailureState state, Instant now) {
        return state.lockedUntil() != null && !now.isBefore(state.lockedUntil());
    }

    public void clearFailedLogins(String normalizedEmail, String clientIp) {
        if (normalizedEmail != null && clientIp != null) {
            loginFailuresByEmailIp.invalidate(normalizedEmail + ":" + clientIp);
        }
    }

    public void clear() {
        buckets.invalidateAll();
        loginFailuresByEmailIp.invalidateAll();
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
