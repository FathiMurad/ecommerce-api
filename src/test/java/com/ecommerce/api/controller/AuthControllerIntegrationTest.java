package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should successfully register a new customer and return JWT token")
    void testRegisterSuccess() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder().firstName("Test").lastName("Customer").email("test.customer@example.com").password("Password@123").build();

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(registerRequest))).andExpect(status().isCreated()).andExpect(jsonPath("$.token").isString()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Registration: Rejects duplicate email with 409 Conflict and generic message")
    void testRegisterDuplicateEmailFailsWithConflict() throws Exception {
        RegisterRequest initialRequest = RegisterRequest.builder().firstName("First").lastName("User").email("duplicate.test@example.com").password("Password@123").build();

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(initialRequest))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(initialRequest))).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409)).andExpect(jsonPath("$.error").value("Conflict")).andExpect(jsonPath("$.message").value("Registration could not be completed with the provided details.")).andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    @DisplayName("Security: Access with valid JWT token of deactivated user is rejected with 401 Unauthorized")
    void testDeactivatedUserTokenAccessFails() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder().firstName("Active").lastName("User").email("deactivated.check@example.com").password("Password@123").build();

        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(registerRequest))).andExpect(status().isCreated()).andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = jsonNode.get("token").asText();

        User user = userRepository.findByEmail("deactivated.check@example.com").orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/orders/my-orders").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.error").value("Unauthorized")).andExpect(jsonPath("$.timestamp").isString());
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

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(loginRequest))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.error").value("Unauthorized")).andExpect(jsonPath("$.message").value("Invalid email or password.")).andExpect(jsonPath("$.timestamp").isString());
    }
}
