package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.entity.enums.PaymentMethod;
import com.ecommerce.api.util.PageRequests;
import com.ecommerce.api.util.SortFields;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewFixesVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String obtainAdminToken() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder().email("admin@ecommerce.com").password("Admin@123456").build();
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(loginRequest))).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("Checkout merges guest cart items into existing user cart instead of dropping them")
    void shouldMergeGuestCartIntoUserCartOnCheckout() throws Exception {
        RegisterRequest register = RegisterRequest.builder().firstName("Merge").lastName("Tester").email("merge.checkout@example.com").password("Password@123").build();
        MvcResult regRes = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(register))).andExpect(status().isCreated()).andReturn();
        String userToken = objectMapper.readTree(regRes.getResponse().getContentAsString()).get("token").asText();

        // 1. User adds product 1 (qty 1) to their authenticated cart
        mockMvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + userToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new AddToCartRequest(1L, 1)))).andExpect(status().isOk());

        // 2. Guest adds product 2 (qty 2) to an anonymous cart
        MvcResult guestCartRes = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new AddToCartRequest(2L, 2)))).andExpect(status().isOk()).andReturn();
        String guestCartToken = objectMapper.readTree(guestCartRes.getResponse().getContentAsString()).get("cartToken").asText();

        // 3. Authenticated user checks out passing X-Cart-Token -> Both products must be in the order!
        CheckoutRequest checkout = new CheckoutRequest("Merge Tester", "merge.checkout@example.com", "10 Main St");
        mockMvc.perform(post("/api/orders/checkout").header("Authorization", "Bearer " + userToken).header("X-Cart-Token", guestCartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkout))).andExpect(status().isCreated()).andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("Declined payment leaves order PENDING and blocks after 5 failed attempts with 409 Conflict")
    void shouldKeepOrderPendingOnDeclineAndBlockAfterFiveAttempts() throws Exception {
        MvcResult cartRes = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new AddToCartRequest(1L, 1)))).andExpect(status().isOk()).andReturn();
        String cartToken = objectMapper.readTree(cartRes.getResponse().getContentAsString()).get("cartToken").asText();

        CheckoutRequest checkout = new CheckoutRequest("Retry Tester", "retry@example.com", "20 Main St");
        MvcResult orderRes = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkout))).andExpect(status().isCreated()).andReturn();
        JsonNode orderNode = objectMapper.readTree(orderRes.getResponse().getContentAsString());
        String trackingNumber = orderNode.get("trackingNumber").asText();
        BigDecimal amount = new BigDecimal(orderNode.get("totalAmount").asText());

        PaymentRequest declinedRequest = PaymentRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).amount(amount).cardNumber("4580000000000000").cardHolderName("Retry Tester").expiryDate("12/28").cvv("123").build();

        // Attempts 1..5 return 200 with FAILED payment status, order stays PENDING
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/payments/orders/" + trackingNumber).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(declinedRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));

            mockMvc.perform(get("/api/orders/" + trackingNumber)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        }

        // 6th attempt must be rejected with 409 Conflict
        mockMvc.perform(post("/api/payments/orders/" + trackingNumber).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(declinedRequest))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Mismatched payment amount returns 422 Unprocessable Content")
    void shouldReturn422WhenPaymentAmountMismatchesOrderTotal() throws Exception {
        MvcResult cartRes = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new AddToCartRequest(1L, 1)))).andExpect(status().isOk()).andReturn();
        String cartToken = objectMapper.readTree(cartRes.getResponse().getContentAsString()).get("cartToken").asText();

        CheckoutRequest checkout = new CheckoutRequest("Mismatch Tester", "mismatch@example.com", "30 Main St");
        MvcResult orderRes = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkout))).andExpect(status().isCreated()).andReturn();
        String trackingNumber = objectMapper.readTree(orderRes.getResponse().getContentAsString()).get("trackingNumber").asText();

        PaymentRequest wrongAmountRequest = PaymentRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).amount(new BigDecimal("999.99")).cardNumber("4580123456789012").cardHolderName("Mismatch Tester").expiryDate("12/28").cvv("123").build();

        mockMvc.perform(post("/api/payments/orders/" + trackingNumber).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(wrongAmountRequest))).andExpect(status().is(422));
    }

    @Test
    @DisplayName("Conflict HTTP 409 returned for invalid state transitions and deleting category with products")
    void shouldReturn409ConflictForStateViolations() throws Exception {
        String adminToken = obtainAdminToken();

        // Deleting category 1 (contains products) -> 409 Conflict
        mockMvc.perform(delete("/api/categories/1").header("Authorization", "Bearer " + adminToken)).andExpect(status().isConflict());

        // Order invalid state transition -> 409 Conflict
        MvcResult cartRes = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new AddToCartRequest(1L, 1)))).andExpect(status().isOk()).andReturn();
        String cartToken = objectMapper.readTree(cartRes.getResponse().getContentAsString()).get("cartToken").asText();

        CheckoutRequest checkout = new CheckoutRequest("State Tester", "state@example.com", "40 Main St");
        MvcResult orderRes = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkout))).andExpect(status().isCreated()).andReturn();
        String trackingNumber = objectMapper.readTree(orderRes.getResponse().getContentAsString()).get("trackingNumber").asText();

        mockMvc.perform(patch("/api/orders/" + trackingNumber + "/status").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(OrderStatus.SHIPPED)))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Order sort whitelist allows createdAt sorting")
    void shouldAllowCreatedAtInOrderSortWhitelist() {
        Pageable requested = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Pageable safe = PageRequests.safe(requested, SortFields.ORDER_SORT_FIELDS, "id");
        assertThat(safe.getSort().getOrderFor("createdAt")).isNotNull();
    }
}
