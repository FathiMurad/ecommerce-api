package com.ecommerce.api.security.ratelimit;

import com.ecommerce.api.security.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces token-bucket rate limits on sensitive endpoints (auth, checkout, payment, and cart).
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RETRY_AFTER = "Retry-After";

    private final RateLimiterService rateLimiterService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimitType type = resolveRateLimitType(request);
        if (type == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        RateLimitResult result = rateLimiterService.tryAcquire(clientIp, type);

        if (result.allowed()) {
            response.setHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(result.remainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader(HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
        JsonErrorWriter.write(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Please retry after " + result.retryAfterSeconds() + " seconds."
        );
    }

    private RateLimitType resolveRateLimitType(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/auth/")) {
            return RateLimitType.AUTH;
        }
        if ("/api/orders/checkout".equals(path) && "POST".equalsIgnoreCase(method)) {
            return RateLimitType.CHECKOUT;
        }
        if (path.startsWith("/api/payments/")) {
            return RateLimitType.PAYMENT;
        }
        if ("/api/cart".equals(path) || path.startsWith("/api/cart/")) {
            return RateLimitType.CART;
        }

        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        // Use socket remote address directly to prevent X-Forwarded-For spoofing
        return request.getRemoteAddr();
    }
}
