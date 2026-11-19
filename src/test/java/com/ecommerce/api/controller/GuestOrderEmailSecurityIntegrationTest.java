package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying account takeover prevention and isolation between guest orders and registered accounts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestOrderEmailSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Newly registered customer with same email as prior guest order cannot access it via email lookup")
    void testRegisteredCustomerCannotAccessGuestOrderViaEmailSearch() throws Exception {
        String victimEmail = "victim-" + UUID.randomUUID() + "@example.com";

        // 1. Create a guest order with victimEmail
        AddToCartRequest addRequest = new AddToCartRequest(1L, 1);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isOk())
                .andReturn();
        String cartToken = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("cartToken")
                .asText();

        CheckoutRequest guestCheckout = new CheckoutRequest(
                "Guest Victim",
                victimEmail,
                "Secret Street 42, Tel Aviv"
        );
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("X-Cart-Token", cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(guestCheckout)))
                .andExpect(status().isCreated())
                .andReturn();
        String guestTrackingNumber = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("trackingNumber")
                .asText();

        // 2. Register a new customer account using the exact same email address
        RegisterRequest registerRequest = RegisterRequest.builder()
                .firstName("Account")
                .lastName("Squatter")
                .email(victimEmail)
                .password("Password@123")
                .build();
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String customerToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        // 3. Verify customer is blocked (403) from email-based order lookup
        mockMvc.perform(get("/api/orders")
                        .param("customerEmail", victimEmail)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // 4. Verify /my-orders does NOT include the unlinked guest order
        mockMvc.perform(get("/api/orders/my-orders")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // 5. Verify ROLE_ADMIN can still query orders by customer email
        LoginRequest adminLogin = LoginRequest.builder()
                .email("admin@ecommerce.com")
                .password("Admin@123456")
                .build();
        MvcResult adminLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode adminNode = objectMapper.readTree(adminLoginResult.getResponse().getContentAsString());
        String adminToken = adminNode.has("token")
                ? adminNode.get("token").asText()
                : adminNode.get("accessToken").asText();

        mockMvc.perform(get("/api/orders")
                        .param("customerEmail", victimEmail)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].trackingNumber").value(guestTrackingNumber));
    }
}
