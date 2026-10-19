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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    @DisplayName("Should throttle authentication endpoint with HTTP 429 when burst limit is exceeded")
    void testAuthRateLimitTriggeredAfterBurstExceeded() throws Exception {
        String testIp = "198.51.100.1";
        LoginRequest badRequest = LoginRequest.builder().email("rate.test@example.com").password("InvalidPass123").build();
        String payload = objectMapper.writeValueAsString(badRequest);

        // Exhaust the 20 tokens allocated for the test IP
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", testIp).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        }

        // The 21st request must be throttled with HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", testIp).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.error").value("Too Many Requests")).andExpect(jsonPath("$.message").value(containsString("Rate limit exceeded"))).andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Should isolate rate limits per client IP so that other IPs remain unaffected")
    void testRateLimitClientIpIsolation() throws Exception {
        String blockedIp = "198.51.100.2";
        String freshIp = "198.51.100.3";
        LoginRequest badRequest = LoginRequest.builder().email("rate.test@example.com").password("InvalidPass123").build();
        String payload = objectMapper.writeValueAsString(badRequest);

        // Exhaust tokens for blockedIp
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", blockedIp).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        }

        // Blocked IP receives 429
        mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", blockedIp).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isTooManyRequests());

        // Different IP (freshIp) must proceed normally (401 Bad Credentials, not 429)
        mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", freshIp).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Public catalog endpoints should not be constrained by auth rate limits")
    void testPublicCatalogEndpointsNotConstrainedByRateLimits() throws Exception {
        String testIp = "198.51.100.4";
        LoginRequest badRequest = LoginRequest.builder().email("rate.test@example.com").password("InvalidPass123").build();
        String payload = objectMapper.writeValueAsString(badRequest);

        // Exhaust tokens on auth endpoint for testIp
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", testIp).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        }

        // Auth endpoint is blocked
        mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", testIp).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isTooManyRequests());

        // Public catalog endpoint must remain accessible (200 OK)
        mockMvc.perform(get("/api/products").header("X-Forwarded-For", testIp)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should throttle checkout endpoint with HTTP 429 when checkout burst limit is exceeded")
    void testCheckoutRateLimitTriggeredAfterBurstExceeded() throws Exception {
        String testIp = "198.51.100.5";

        // Exhaust checkout burst tokens
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/orders/checkout").header("X-Forwarded-For", testIp).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        }

        // The 21st checkout request must be throttled with HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/orders/checkout").header("X-Forwarded-For", testIp).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.error").value("Too Many Requests"));
    }
}
