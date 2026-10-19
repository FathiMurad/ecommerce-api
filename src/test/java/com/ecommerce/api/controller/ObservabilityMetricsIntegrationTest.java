package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.entity.enums.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"management.endpoints.web.exposure.include=health,info,prometheus", "management.endpoint.prometheus.enabled=true", "management.prometheus.metrics.export.enabled=true"})
@AutoConfigureMockMvc
class ObservabilityMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Actuator health endpoint should return UP status without sensitive details")
    void testHealthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator Prometheus endpoint should expose metrics in Prometheus exposition format")
    void testPrometheusEndpointExposesMetrics() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("Executing checkout and payment should record business metrics in Prometheus registry")
    void testBusinessMetricsRecordedAfterCheckoutAndPayment() throws Exception {
        // 1. Add item to guest cart
        AddToCartRequest addRequest = new AddToCartRequest(1L, 1);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andReturn();

        String cartToken = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("cartToken").asText();

        // 2. Perform Checkout -> triggers ecommerce_orders_total
        CheckoutRequest checkoutRequest = new CheckoutRequest("Metrics Buyer", "metrics@example.com", "Herzl St 50, Tel Aviv");
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkoutRequest))).andExpect(status().isCreated()).andReturn();

        String trackingNumber = objectMapper.readTree(checkoutResult.getResponse().getContentAsString()).get("trackingNumber").asText();

        // 3. Process Approved Payment -> triggers ecommerce_payments_processed_total & latency timer
        PaymentRequest paymentRequest = PaymentRequest.builder().paymentMethod(PaymentMethod.CREDIT_CARD).cardNumber("4580123456789999").amount(new BigDecimal("6.50")).build();

        mockMvc.perform(post("/api/payments/orders/" + trackingNumber).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(paymentRequest))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. Verify Prometheus registry contains custom business metrics
        MvcResult metricsResult = mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn();

        String prometheusOutput = metricsResult.getResponse().getContentAsString();

        assertThat(prometheusOutput).contains("ecommerce_orders_total");
        assertThat(prometheusOutput).contains("ecommerce_payments_processed_total");
        assertThat(prometheusOutput).contains("ecommerce_payment_processing_time_seconds");
    }
}
