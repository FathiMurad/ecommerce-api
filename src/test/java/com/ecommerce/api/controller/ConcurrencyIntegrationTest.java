package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.ProductRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Atomic Stock Update: Prevent overselling and false version collisions when multiple threads checkout in parallel")
    void testConcurrentCheckoutPreventsOverselling() throws Exception {
        // 1. Arrange: Create a dedicated product with exactly 3 units in inventory
        Category category = categoryRepository.findById(1L).orElseGet(() -> categoryRepository.save(Category.builder().name("Electronics").description("Test").build()));

        int initialStock = 3;
        int concurrentUsers = 10;

        Product limitedProduct = productRepository.saveAndFlush(Product.builder().name("Limited Edition RTX 5090").description("Exclusive limited stock item").price(new BigDecimal("1999.99")).stockQuantity(initialStock).category(category).images(Collections.emptyList()).build());

        Long productId = limitedProduct.getId();

        // 2. Pre-create distinct shopping carts for all concurrent buyers (each containing 1 unit)
        List<String> cartTokens = new ArrayList<>();
        for (int i = 0; i < concurrentUsers; i++) {
            final int index = i;
            AddToCartRequest addRequest = new AddToCartRequest(productId, 1);
            MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest)).with(request -> {
                request.setRemoteAddr("192.168.100." + index);
                return request;
            })).andExpect(status().isOk()).andReturn();

            JsonNode cartNode = objectMapper.readTree(addResult.getResponse().getContentAsString());
            cartTokens.add(cartNode.get("cartToken").asText());
        }

        // 3. Act: Fire concurrent checkout requests simultaneously using CountDownLatch
        CountDownLatch readyLatch = new CountDownLatch(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger();
        ConcurrentLinkedQueue<String> conflictBodies = new ConcurrentLinkedQueue<>();
        AtomicInteger unexpectedStatusCount = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers)) {
            for (int i = 0; i < concurrentUsers; i++) {
                final String cartToken = cartTokens.get(i);
                final int index = i;

                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();

                        CheckoutRequest checkoutRequest = new CheckoutRequest("Concurrent Buyer " + index, "buyer" + index + "@concurrency.com", "Main Street " + (100 + index) + ", Tel Aviv");

                        MvcResult result = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkoutRequest)).with(request -> {
                            request.setRemoteAddr("192.168.100." + index);
                            return request;
                        })).andReturn();

                        int statusCode = result.getResponse().getStatus();
                        if (statusCode == 201) {
                            successCount.incrementAndGet();
                        } else if (statusCode == 409) {
                            conflictBodies.add(result.getResponse().getContentAsString());
                        } else {
                            unexpectedStatusCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedStatusCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean allReady = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(allReady).as("All worker threads should be initialized and ready").isTrue();

            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            assertThat(completed).as("All checkout requests should complete within timeout").isTrue();
        }

        // 4. Assert: Exactly 3 transactions succeeded, 7 rejected due to InsufficientStockException, and stock is 0
        assertThat(successCount.get()).as("Exactly %d customers should successfully purchase the available units", initialStock).isEqualTo(initialStock);

        assertThat(conflictBodies).as("Remaining concurrent requests must be rejected with 409 Conflict").hasSize(concurrentUsers - initialStock).allSatisfy(body -> assertThat(body).as("409 response must be caused by InsufficientStockException, not Hibernate version collision").contains("Insufficient stock"));

        assertThat(unexpectedStatusCount.get()).as("No unexpected status codes or unhandled exceptions should occur").isZero();

        Product finalProductState = productRepository.findById(productId).orElseThrow();
        assertThat(finalProductState.getStockQuantity()).as("Database stock level must be exactly 0 (no negative stock or overselling)").isZero();
    }
}
