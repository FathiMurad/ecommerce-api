package com.ecommerce.api.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filter enforcing token-bucket rate limits on sensitive endpoints (Auth, Checkout)
 * to guard against brute-force attacks and denial-of-service abuse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        RateLimitType type = resolveRateLimitType(request);

        if (type == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        RateLimitResult result = rateLimiterService.tryAcquire(clientIp, type);

        if (result.allowed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        handleRateLimitExceeded(response, result);
    }

    private RateLimitType resolveRateLimitType(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/auth/")) {
            return RateLimitType.AUTH;
        }

        if (path.equals("/api/orders/checkout") && "POST".equalsIgnoreCase(method)) {
            return RateLimitType.CHECKOUT;
        }

        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void handleRateLimitExceeded(HttpServletResponse response, RateLimitResult result) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        errorBody.put("error", "Too Many Requests");
        errorBody.put("message", "Rate limit exceeded. Please retry after " + result.retryAfterSeconds() + " seconds.");
        errorBody.put("timestamp", Instant.now().toString());

        objectMapper.writeValue(response.getOutputStream(), errorBody);
    }
}
