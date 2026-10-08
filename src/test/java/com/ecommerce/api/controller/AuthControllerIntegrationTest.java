package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should successfully register a new customer and return JWT token")
    void testRegisterSuccess() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder().firstName("Test").lastName("Customer").email("test.customer@example.com").password("Password@123").build();

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(registerRequest))).andExpect(status().isCreated()).andExpect(jsonPath("$.token").isString()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Should successfully login seeded Admin and return JWT token")
    void testAdminLoginSuccess() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder().email("admin@ecommerce.com").password("Admin@123456").build();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(loginRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Should reject login with bad credentials")
    void testLoginInvalidCredentials() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder().email("admin@ecommerce.com").password("WrongPassword999").build();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(loginRequest))).andExpect(status().isUnauthorized());
    }
}
