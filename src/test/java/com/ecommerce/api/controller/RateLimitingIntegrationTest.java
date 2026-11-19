package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.security.ratelimit.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying IP and account-based rate limiting, spoofing protection, and endpoint isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiterService rateLimiterService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        rateLimiterService.clear();
    }

    private RequestPostProcessor remoteIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    @DisplayName("Should throttle authentication endpoint with HTTP 429 when burst limit is exceeded")
    void testAuthRateLimitTriggeredAfterBurstExceeded() throws Exception {
        String testIp = "198.51.100.1";

        for (int i = 0; i < 20; i++) {
            LoginRequest badRequest = LoginRequest.builder()
                    .email("rate.ip." + i + "@example.com")
                    .password("InvalidPass123")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .with(remoteIp(testIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest blockedRequest = LoginRequest.builder()
                .email("rate.ip.20@example.com")
                .password("InvalidPass123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteIp(testIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockedRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value(containsString("Rate limit exceeded")))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Should ignore spoofed X-Forwarded-For headers from untrusted clients")
    void testSpoofedXForwardedForCannotBypassRateLimit() throws Exception {
        String realClientIp = "198.51.100.10";

        for (int i = 0; i < 20; i++) {
            LoginRequest badRequest = LoginRequest.builder()
                    .email("spoof.test." + i + "@example.com")
                    .password("InvalidPass123")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .with(remoteIp(realClientIp))
                            .header("X-Forwarded-For", "203.0.113." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest blockedRequest = LoginRequest.builder()
                .email("spoof.test.20@example.com")
                .password("InvalidPass123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteIp(realClientIp))
                        .header("X-Forwarded-For", "203.0.113.250")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockedRequest)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Should lock out normalized email and IP combination with HTTP 429 after 5 failed login attempts from same IP")
    void testLoginLockoutByNormalizedEmailAndIpAfter5Failures() throws Exception {
        String targetEmail = "victim@example.com";
        String clientIp = "198.51.100.50";

        for (int i = 0; i < 5; i++) {
            LoginRequest badRequest = LoginRequest.builder()
                    .email(targetEmail)
                    .password("WrongPass" + i)
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .with(remoteIp(clientIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest lockedRequest = LoginRequest.builder()
                .email("VICTIM@Example.COM")
                .password("AnyPassword123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteIp(clientIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockedRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("Failed login attempts from an attacker IP do not lock out user on a clean victim IP")
    void testLoginLockoutIsIsolatedPerIp() throws Exception {
        String targetEmail = "isolated@example.com";
        String attackerIp = "203.0.113.50";
        String victimIp = "198.51.100.80";

        for (int i = 0; i < 5; i++) {
            LoginRequest badRequest = LoginRequest.builder()
                    .email(targetEmail)
                    .password("WrongPass" + i)
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .with(remoteIp(attackerIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest attackerRetry = LoginRequest.builder()
                .email(targetEmail)
                .password("AnyPassword123")
                .build();
        mockMvc.perform(post("/api/auth/login")
                        .with(remoteIp(attackerIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attackerRetry)))
                .andExpect(status().isTooManyRequests());

        LoginRequest victimRequest = LoginRequest.builder()
                .email(targetEmail)
                .password("AnyPassword123")
                .build();
        mockMvc.perform(post("/api/auth/login")
                        .with(remoteIp(victimIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(victimRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should isolate rate limits per client IP so that other IPs remain unaffected")
    void testRateLimitClientIpIsolation() throws Exception {
        String blockedIp = "198.51.100.2";
        String freshIp = "198.51.100.3";

        for (int i = 0; i < 20; i++) {
            LoginRequest badRequest = LoginRequest.builder()
                    .email("iso.test." + i + "@example.com")
                    .password("InvalidPass123")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .with(remoteIp(blockedIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest extraRequest = LoginRequest.builder()
                .email("iso.extra@example.com")
                .password("InvalidPass123")
                .build();
        String payload = objectMapper.writeValueAsString(extraRequest);

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteIp(blockedIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteIp(freshIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Public catalog endpoints should not be constrained by auth rate limits")
    void testPublicCatalogEndpointsNotConstrainedByRateLimits() throws Exception {
        String testIp = "198.51.100.4";

        for (int i = 0; i < 20; i++) {
            LoginRequest badRequest = LoginRequest.builder()
                    .email("pub.test." + i + "@example.com")
                    .password("InvalidPass123")
                    .build();

            mockMvc.perform(post("/api/auth/login")
                            .with(remoteIp(testIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest extraRequest = LoginRequest.builder()
                .email("pub.extra@example.com")
                .password("InvalidPass123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteIp(testIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extraRequest)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/products")
                        .with(remoteIp(testIp)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should throttle checkout endpoint with HTTP 429 when checkout burst limit is exceeded")
    void testCheckoutRateLimitTriggeredAfterBurstExceeded() throws Exception {
        String testIp = "198.51.100.5";

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/orders/checkout")
                            .with(remoteIp(testIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/orders/checkout")
                        .with(remoteIp(testIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    @Test
    @DisplayName("Should throttle payment endpoints with HTTP 429 when payment burst limit is exceeded")
    void testPaymentRateLimitTriggeredAfterBurstExceeded() throws Exception {
        String testIp = "198.51.100.6";

        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/payments/orders/ORD-NONEXISTENT")
                            .with(remoteIp(testIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/payments/orders/ORD-NONEXISTENT")
                        .with(remoteIp(testIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("Should throttle all cart endpoints (GET, POST, PUT, DELETE) with HTTP 429 when burst limit is exceeded")
    void testAllCartEndpointsRateLimitedAfterBurstExceeded() throws Exception {
        String testIp = "198.51.100.7";

        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/cart")
                            .with(remoteIp(testIp)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/cart")
                        .with(remoteIp(testIp)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        mockMvc.perform(post("/api/cart/items")
                        .with(remoteIp(testIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(put("/api/cart/items/1")
                        .with(remoteIp(testIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(delete("/api/cart/items/1")
                        .with(remoteIp(testIp)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(delete("/api/cart")
                        .with(remoteIp(testIp)))
                .andExpect(status().isTooManyRequests());
    }
}
