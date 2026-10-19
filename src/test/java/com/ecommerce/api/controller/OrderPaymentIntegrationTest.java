package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.dto.request.UpdateOrderStatusRequest;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.entity.enums.PaymentMethod;
import com.ecommerce.api.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderPaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String obtainAdminToken() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder().email("admin@ecommerce.com").password("Admin@123456").build();

        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(loginRequest))).andExpect(status().isOk()).andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        if (root.has("token")) {
            return root.get("token").asText();
        } else if (root.has("accessToken")) {
            return root.get("accessToken").asText();
        }
        return root.get("jwt").asText();
    }

    @Test
    @DisplayName("End-to-End Guest Checkout & Payment Lifecycle")
    void testGuestCheckoutAndPaymentFlow() throws Exception {
        // 1. Add product #1 to a fresh guest cart
        AddToCartRequest addRequest = new AddToCartRequest(1L, 2);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.cartToken").isString()).andReturn();

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

        // 4. Charge payment with matching amount -> Expect COMPLETED
        PaymentRequest paymentRequest = PaymentRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).amount(totalAmount).cardNumber("4580123456789012").cardHolderName("Guest Tester").expiryDate("12/28").cvv("123").build();

        mockMvc.perform(post("/api/payments/orders/" + trackingNumber).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(paymentRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.transactionId").isString());

        // 5. Verify order status advanced to CONFIRMED
        mockMvc.perform(get("/api/orders/" + trackingNumber)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Enforce State Machine Transitions and Guard Terminal States")
    void testOrderLifecycleStateMachineTransitions() throws Exception {
        String adminToken = obtainAdminToken();

        // 1. Checkout order -> PENDING
        AddToCartRequest addRequest = new AddToCartRequest(1L, 1);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andReturn();

        String cartToken = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("cartToken").asText();

        CheckoutRequest checkoutRequest = new CheckoutRequest("Lifecycle Tester", "sm@example.com", "Herzl St 10, Haifa");
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkoutRequest))).andExpect(status().isCreated()).andReturn();

        String trackingNumber = objectMapper.readTree(checkoutResult.getResponse().getContentAsString()).get("trackingNumber").asText();

        // 2. Illegal transition: Cannot jump directly from PENDING to SHIPPED -> Expect 400 Bad Request
        UpdateOrderStatusRequest illegalJump = new UpdateOrderStatusRequest(OrderStatus.SHIPPED);
        mockMvc.perform(patch("/api/orders/" + trackingNumber + "/status").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(illegalJump))).andExpect(status().isBadRequest());

        // 3. Legal transition: PENDING -> CONFIRMED
        UpdateOrderStatusRequest confirmRequest = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);
        mockMvc.perform(patch("/api/orders/" + trackingNumber + "/status").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(confirmRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 4. Legal transition: CONFIRMED -> SHIPPED
        UpdateOrderStatusRequest shipRequest = new UpdateOrderStatusRequest(OrderStatus.SHIPPED);
        mockMvc.perform(patch("/api/orders/" + trackingNumber + "/status").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(shipRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"));

        // 5. Legal transition: SHIPPED -> DELIVERED (Terminal)
        UpdateOrderStatusRequest deliverRequest = new UpdateOrderStatusRequest(OrderStatus.DELIVERED);
        mockMvc.perform(patch("/api/orders/" + trackingNumber + "/status").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(deliverRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));

        // 6. Terminal state guard: Cannot modify an order already in DELIVERED status -> Expect 400 Bad Request
        UpdateOrderStatusRequest modifyDelivered = new UpdateOrderStatusRequest(OrderStatus.CANCELLED);
        mockMvc.perform(patch("/api/orders/" + trackingNumber + "/status").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(modifyDelivered))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin Cancellation Restores Product Stock Accurately")
    void testAdminCancellationRestoresStock() throws Exception {
        String adminToken = obtainAdminToken();

        entityManager.flush();
        entityManager.clear();
        Product productBefore = productRepository.findById(1L).orElseThrow();
        int initialStock = productBefore.getStockQuantity();

        // 1. Checkout 3 units of product #1
        AddToCartRequest addRequest = new AddToCartRequest(1L, 3);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andReturn();

        String cartToken = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("cartToken").asText();

        CheckoutRequest checkoutRequest = new CheckoutRequest("Cancel Tester", "cancel@example.com", "Dizengoff 100, Tel Aviv");
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkoutRequest))).andExpect(status().isCreated()).andReturn();

        String trackingNumber = objectMapper.readTree(checkoutResult.getResponse().getContentAsString()).get("trackingNumber").asText();

        // Verify stock decreased by 3
        entityManager.flush();
        entityManager.clear();
        Product productAfterCheckout = productRepository.findById(1L).orElseThrow();
        assertThat(productAfterCheckout.getStockQuantity()).isEqualTo(initialStock - 3);

        // 2. Admin cancels order -> Expect CANCELLED and stock restored
        UpdateOrderStatusRequest cancelRequest = new UpdateOrderStatusRequest(OrderStatus.CANCELLED);
        mockMvc.perform(patch("/api/orders/" + trackingNumber + "/status").header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cancelRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        // 3. Verify stock restored back to initial level
        entityManager.flush();
        entityManager.clear();
        Product productAfterCancel = productRepository.findById(1L).orElseThrow();
        assertThat(productAfterCancel.getStockQuantity()).isEqualTo(initialStock);
    }

    @Test
    @DisplayName("Payment Decline Automatically Cancels Order and Restores Stock")
    void testPaymentDeclineCancelsOrderAndRestoresStock() throws Exception {
        entityManager.flush();
        entityManager.clear();
        Product productBefore = productRepository.findById(1L).orElseThrow();
        int initialStock = productBefore.getStockQuantity();

        // 1. Checkout 2 units
        AddToCartRequest addRequest = new AddToCartRequest(1L, 2);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andReturn();

        String cartToken = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("cartToken").asText();

        CheckoutRequest checkoutRequest = new CheckoutRequest("Decline Tester", "decline@example.com", "Rabin Sq 1, Tel Aviv");
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkoutRequest))).andExpect(status().isCreated()).andReturn();

        JsonNode checkoutNode = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        String trackingNumber = checkoutNode.get("trackingNumber").asText();
        BigDecimal totalAmount = new BigDecimal(checkoutNode.get("totalAmount").asText());

        // Verify stock decreased by 2
        entityManager.flush();
        entityManager.clear();
        Product productAfterCheckout = productRepository.findById(1L).orElseThrow();
        assertThat(productAfterCheckout.getStockQuantity()).isEqualTo(initialStock - 2);

        // 2. Charge using card ending in 0000 -> Simulates payment decline
        PaymentRequest declinedPayment = PaymentRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).amount(totalAmount).cardNumber("4580000000000000").cardHolderName("Decline Tester").expiryDate("12/28").cvv("123").build();

        mockMvc.perform(post("/api/payments/orders/" + trackingNumber).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(declinedPayment))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));

        // 3. Verify order status marked as CANCELLED
        mockMvc.perform(get("/api/orders/" + trackingNumber)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        // 4. Verify stock automatically restored back to initial level
        entityManager.flush();
        entityManager.clear();
        Product productAfterFailedPayment = productRepository.findById(1L).orElseThrow();
        assertThat(productAfterFailedPayment.getStockQuantity()).isEqualTo(initialStock);
    }
}
