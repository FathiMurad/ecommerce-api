package com.ecommerce.api.event;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderEventAsyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderEventListener orderEventListener;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orderEventListener.reset(1);
    }

    @Test
    @DisplayName("Should dispatch OrderPlacedEvent after transaction commit and process on dedicated thread pool")
    void testOrderPlacedEventDispatchedAndProcessedAsynchronously() throws Exception {
        // 1. Add product #1 to guest cart
        AddToCartRequest addRequest = new AddToCartRequest(1L, 1);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andReturn();

        String cartToken = objectMapper.readTree(addResult.getResponse().getContentAsString()).get("cartToken").asText();

        // 2. Execute Checkout -> Triggers commit and OrderPlacedEvent publication
        CheckoutRequest checkoutRequest = new CheckoutRequest("Event Tester", "async.tester@example.com", "Rabin St 12, Haifa");
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkoutRequest))).andExpect(status().isCreated()).andExpect(jsonPath("$.trackingNumber").isString()).andReturn();

        JsonNode checkoutNode = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        String trackingNumber = checkoutNode.get("trackingNumber").asText();

        // 3. Await asynchronous event listener execution deterministically without busy-waiting
        boolean completed = orderEventListener.getLatch().await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 4. Assert event handled successfully
        assertThat(orderEventListener.getProcessedEventCount()).isGreaterThanOrEqualTo(1);

        OrderPlacedEvent capturedEvent = orderEventListener.getLastProcessedEvent();
        assertThat(capturedEvent).isNotNull();
        assertThat(capturedEvent.trackingNumber()).isEqualTo(trackingNumber);
        assertThat(capturedEvent.customerEmail()).isEqualTo("async.tester@example.com");
        assertThat(capturedEvent.customerName()).isEqualTo("Event Tester");

        // 5. Assert execution thread belonged to dedicated thread pool prefix
        String workerThread = orderEventListener.getExecutionThreadName();
        assertThat(workerThread).isNotNull();
        assertThat(workerThread).startsWith("order-event-");
    }
}
