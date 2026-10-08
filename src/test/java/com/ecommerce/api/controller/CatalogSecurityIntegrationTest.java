package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.ProductRequest;
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

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Public catalog browsing: GET /api/products allows unauthenticated access")
    void testPublicCatalogAccess() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("RBAC: POST /api/products rejects unauthenticated requests with 403 Forbidden")
    void testCreateProductUnauthenticatedFails() throws Exception {
        ProductRequest productRequest = ProductRequest.builder().name("Unauthorized Item").description("Should be rejected").price(new BigDecimal("99.99")).stockQuantity(10).categoryId(1L).imageUrls(List.of()).build();

        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(productRequest))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: POST /api/products rejects regular CUSTOMER role with 403 Forbidden")
    void testCreateProductCustomerRoleForbidden() throws Exception {
        // 1. Register a regular customer
        RegisterRequest register = RegisterRequest.builder().firstName("Buyer").lastName("User").email("buyer.role@example.com").password("Password@123").build();

        MvcResult regResult = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(register))).andExpect(status().isCreated()).andReturn();

        JsonNode jsonNode = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String customerToken = jsonNode.get("token").asText();

        // 2. Customer attempts an admin action
        ProductRequest productRequest = ProductRequest.builder().name("Hacker Product").description("Should be rejected by role checker").price(new BigDecimal("10.00")).stockQuantity(5).categoryId(1L).imageUrls(List.of()).build();

        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + customerToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(productRequest))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: POST /api/products succeeds when executed by ADMIN role")
    void testCreateProductAdminRoleSuccess() throws Exception {
        // 1. Login as seeded Admin
        LoginRequest adminLogin = LoginRequest.builder().email("admin@ecommerce.com").password("Admin@123456").build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(adminLogin))).andExpect(status().isOk()).andReturn();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String adminToken = jsonNode.get("token").asText();

        // 2. Admin creates a product
        ProductRequest productRequest = ProductRequest.builder().name("Admin Managed Mouse").description("Ergonomic wireless mouse").price(new BigDecimal("49.99")).stockQuantity(25).categoryId(1L).imageUrls(List.of("https://images.unsplash.com/photo-1527864550417-7fd91fc51a46")).build();

        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(productRequest))).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Admin Managed Mouse")).andExpect(jsonPath("$.price").value(49.99));
    }
}
