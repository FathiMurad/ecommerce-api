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
    @DisplayName("Pessimistic Lock: Prevent overselling when multiple threads checkout the last stock unit in parallel")
    void testConcurrentCheckoutPreventsOverselling() throws Exception {
        // 1. Arrange: Create a dedicated product with exactly 1 unit in inventory
        Category category = categoryRepository.findById(1L).orElseGet(() -> categoryRepository.save(Category.builder().name("Electronics").description("Test").build()));

        Product limitedProduct = productRepository.saveAndFlush(Product.builder().name("Limited Edition RTX 5090").description("Exclusive single stock item").price(new BigDecimal("1999.99")).stockQuantity(1).category(category).images(Collections.emptyList()).build());

        Long productId = limitedProduct.getId();
        int concurrentUsers = 10;

        // 2. Pre-create distinct shopping carts for all concurrent buyers (each containing 1 unit)
        List<String> cartTokens = new ArrayList<>();
        for (int i = 0; i < concurrentUsers; i++) {
            AddToCartRequest addRequest = new AddToCartRequest(productId, 1);
            MvcResult addResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andReturn();

            JsonNode cartNode = objectMapper.readTree(addResult.getResponse().getContentAsString());
            cartTokens.add(cartNode.get("cartToken").asText());
        }

        // 3. Act: Fire concurrent checkout requests simultaneously using CountDownLatch
        CountDownLatch readyLatch = new CountDownLatch(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
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

                        MvcResult result = mockMvc.perform(post("/api/orders/checkout").header("X-Cart-Token", cartToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(checkoutRequest))).andReturn();

                        int statusCode = result.getResponse().getStatus();
                        if (statusCode == 201) {
                            successCount.incrementAndGet();
                        } else if (statusCode == 409) {
                            conflictCount.incrementAndGet();
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

        // 4. Assert: Exactly one transaction succeeded, all others rejected with 409 Conflict, and stock is exactly 0
        assertThat(successCount.get()).as("Exactly one customer should successfully purchase the last unit").isEqualTo(1);

        assertThat(conflictCount.get()).as("All other concurrent requests must be rejected with 409 Conflict due to InsufficientStockException").isEqualTo(concurrentUsers - 1);

        assertThat(unexpectedStatusCount.get()).as("No unexpected status codes or unhandled exceptions should occur").isZero();

        Product finalProductState = productRepository.findById(productId).orElseThrow();
        assertThat(finalProductState.getStockQuantity()).as("Database stock level must be exactly 0 (no negative stock or overselling)").isZero();
    }
}
