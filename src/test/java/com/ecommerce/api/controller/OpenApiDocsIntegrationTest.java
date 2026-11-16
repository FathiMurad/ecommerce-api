package com.ecommerce.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class OpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("GET /v3/api-docs returns valid OpenAPI JSON with accurate security metadata, license, and checkout docs")
    void testOpenApiDocsMetadataAndSecurityAccuracy() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(root.has("openapi")).isTrue();
        assertThat(root.path("info").path("license").path("name").asText()).isEqualTo("MIT License");

        // 1. Public catalog endpoint must NOT require bearerAuth
        JsonNode getProductsOp = root.path("paths").path("/api/products").path("get");
        assertThat(getProductsOp.has("security")).isFalse();

        // 2. Guest-capable cart & checkout endpoints must allow unauthenticated access ({})
        JsonNode getCartSecurity = root.path("paths").path("/api/cart").path("get").path("security");
        assertThat(getCartSecurity.isArray()).isTrue();
        assertThat(getCartSecurity.toString()).contains("{}");

        JsonNode checkoutOp = root.path("paths").path("/api/orders/checkout").path("post");
        assertThat(checkoutOp.path("description").asText()).contains("PENDING").doesNotContain("confirmed order");

        // 3. Protected customer & admin endpoints must require bearerAuth without empty {}
        JsonNode myOrdersSecurity = root.path("paths").path("/api/orders/my-orders").path("get").path("security");
        assertThat(myOrdersSecurity.toString()).contains("bearerAuth").doesNotContain("{}");

        JsonNode adminSearchSecurity = root.path("paths").path("/api/orders").path("get").path("security");
        assertThat(adminSearchSecurity.toString()).contains("bearerAuth").doesNotContain("{}");
    }
}
