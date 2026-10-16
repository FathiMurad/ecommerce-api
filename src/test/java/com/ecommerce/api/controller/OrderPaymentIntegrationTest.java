package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.entity.enums.PaymentMethod;
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
class OrderPaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("End-to-End Guest Checkout & Payment Lifecycle")
    void testGuestCheckoutAndPaymentFlow() throws Exception {
        // 1. Add product #1 to a fresh guest cart
        AddToCartRequest addRequest = new AddToCartRequest(1L, 2);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.cartToken").isString()).andReturn();

        // Extract the authoritative cartToken generated and stored by the server
        JsonNode cartNode = objectMapper.readTree(addResult.getResponse().getContentAsString());
        String cartToken = cartNode.get("cartToken").asText();

        // 2. Perform Checkout -> Expect HTTP 201 with status PENDING
        CheckoutRequest checkoutRequest = new CheckoutRequest("Guest Tester", "guest@example.com", "Allenby St 50, Tel Aviv");

        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkoutRequest))).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.trackingNumber").isString()).andReturn();

        JsonNode checkoutNode = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        String trackingNumber = checkoutNode.get("trackingNumber").asText();
        BigDecimal totalAmount = new BigDecimal(checkoutNode.get("totalAmount").asText());

        // 3. Verify cart was cleared
        mockMvc.perform(get("/api/cart").header("X-Cart-Token", cartToken)).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0)).andExpect(jsonPath("$.items").isEmpty());

        // 4. Charge payment with matching amount via /api/payments/orders/{trackingNumber} -> Expect COMPLETED
        PaymentRequest paymentRequest = PaymentRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).amount(totalAmount).cardNumber("4580123456789012").cardHolderName("Guest Tester").expiryDate("12/28").cvv("123").build();

        mockMvc.perform(post("/api/payments/orders/" + trackingNumber).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(paymentRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.transactionId").isString());

        // 5. Verify order status advanced to CONFIRMED via public tracking lookup
        mockMvc.perform(get("/api/orders/" + trackingNumber)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
