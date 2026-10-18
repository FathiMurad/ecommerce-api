package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String obtainAdminToken() throws Exception {
        LoginRequest adminLogin = LoginRequest.builder().email("admin@ecommerce.com").password("Admin@123456").build();

        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(adminLogin))).andExpect(status().isOk()).andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return responseNode.get("token").asText();
    }

    @Test
    @DisplayName("Should reject invalid checkout request with 400 and validation errors")
    void testCheckoutValidationFailure() throws Exception {
        CheckoutRequest invalidCheckout = new CheckoutRequest("", "not-an-email", "");

        mockMvc.perform(post("/api/orders/checkout").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invalidCheckout))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.validationErrors.customerName").isNotEmpty()).andExpect(jsonPath("$.validationErrors.customerEmail").isNotEmpty()).andExpect(jsonPath("$.validationErrors.shippingAddress").isNotEmpty());
    }

    @Test
    @DisplayName("Should reject invalid register request with 400 and field-level validation errors")
    void testRegisterValidationFailure() throws Exception {
        RegisterRequest invalidRegister = RegisterRequest.builder().firstName("").lastName("").email("invalid-email-address").password("123") // Less than 6 characters
                .build();

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invalidRegister))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.validationErrors.firstName").isNotEmpty()).andExpect(jsonPath("$.validationErrors.lastName").isNotEmpty()).andExpect(jsonPath("$.validationErrors.email").isNotEmpty()).andExpect(jsonPath("$.validationErrors.password").isNotEmpty());
    }

    @Test
    @DisplayName("Should reject invalid cart addition with 400 when quantity is zero or product id is null")
    void testAddToCartValidationFailure() throws Exception {
        AddToCartRequest invalidCartItem = new AddToCartRequest(null, 0);

        mockMvc.perform(post("/api/cart/items").header("X-Cart-Token", "cart-validation-token").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invalidCartItem))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.validationErrors.productId").isNotEmpty()).andExpect(jsonPath("$.validationErrors.quantity").isNotEmpty());
    }

    @Test
    @DisplayName("Should reject product creation with negative price and missing fields")
    void testProductCreationValidationFailure() throws Exception {
        String adminToken = obtainAdminToken();

        ProductRequest invalidProduct = ProductRequest.builder().name("").price(new BigDecimal("-15.00")).stockQuantity(-1).categoryId(null).build();

        mockMvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invalidProduct))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.validationErrors.name").isNotEmpty()).andExpect(jsonPath("$.validationErrors.price").isNotEmpty()).andExpect(jsonPath("$.validationErrors.stockQuantity").isNotEmpty()).andExpect(jsonPath("$.validationErrors.categoryId").isNotEmpty());
    }

    @Test
    @DisplayName("Should return 400 Bad Request for malformed JSON request bodies")
    void testMalformedJsonPayloadReturnsBadRequest() throws Exception {
        String malformedJson = "{\"customerName\": \"Test\", \"customerEmail\": ";

        mockMvc.perform(post("/api/orders/checkout").contentType(MediaType.APPLICATION_JSON).content(malformedJson)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").value("Malformed JSON request body or invalid field format."));
    }

    @Test
    @DisplayName("Should safely handle pagination abuse and malicious sort parameters without throwing 500")
    void testPaginationSafetyEdgeCases() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "-10").param("size", "99999").param("sortBy", "nonExistentField;DROP TABLE products").param("sortDirection", "invalidDirection")).andExpect(status().isOk()).andExpect(jsonPath("$.pageNumber").value(0)).andExpect(jsonPath("$.content").isArray());
    }
}
