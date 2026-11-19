package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.dto.request.CheckoutRequest;
import com.ecommerce.api.dto.request.PaymentRequest;
import com.ecommerce.api.entity.Category;
import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.Product;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.entity.enums.PaymentMethod;
import com.ecommerce.api.entity.enums.PaymentStatus;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.repository.CategoryRepository;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.repository.PaymentRepository;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying concurrency safety, overselling prevention, and idempotency under parallel load.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Atomic Stock Update: Prevent overselling and false version collisions when multiple threads checkout in parallel")
    void testConcurrentCheckoutPreventsOverselling() throws Exception {
        Category category = categoryRepository.findById(1L)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Electronics")
                        .description("Test")
                        .build()));

        int initialStock = 3;
        int concurrentUsers = 10;

        Product limitedProduct = productRepository.saveAndFlush(Product.builder()
                .name("Limited Edition RTX 5090")
                .description("Exclusive limited stock item")
                .price(new BigDecimal("1999.99"))
                .stockQuantity(initialStock)
                .category(category)
                .images(Collections.emptyList())
                .build());

        Long productId = limitedProduct.getId();

        List<String> cartTokens = new ArrayList<>();
        for (int i = 0; i < concurrentUsers; i++) {
            final int index = i;
            AddToCartRequest addRequest = new AddToCartRequest(productId, 1);
            MvcResult addResult = mockMvc.perform(post("/api/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .with(request -> {
                                request.setRemoteAddr("192.168.100." + index);
                                return request;
                            }))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode cartNode = objectMapper.readTree(addResult.getResponse().getContentAsString());
            cartTokens.add(cartNode.get("cartToken").asText());
        }

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

                        CheckoutRequest checkoutRequest = new CheckoutRequest(
                                "Concurrent Buyer " + index,
                                "buyer" + index + "@concurrency.com",
                                "Main Street " + (100 + index) + ", Tel Aviv"
                        );

                        MvcResult result = mockMvc.perform(post("/api/orders/checkout")
                                        .header("X-Cart-Token", cartToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(checkoutRequest))
                                        .with(request -> {
                                            request.setRemoteAddr("192.168.100." + index);
                                            return request;
                                        }))
                                .andReturn();

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

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(conflictBodies)
                .hasSize(concurrentUsers - initialStock)
                .allSatisfy(body -> assertThat(body).contains("Insufficient stock"));
        assertThat(unexpectedStatusCount.get()).isZero();
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("Duplicate concurrent checkout on the exact same cart token persists one order and empties cart")
    void testConcurrentCheckoutSameCartCreatesSingleOrder() throws Exception {
        Category category = categoryRepository.findById(1L)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Electronics")
                        .description("Test")
                        .build()));

        Product product = productRepository.saveAndFlush(Product.builder()
                .name("Single Cart Race Test Item")
                .description("Concurrency guard verification")
                .price(new BigDecimal("50.00"))
                .stockQuantity(10)
                .category(category)
                .images(Collections.emptyList())
                .build());

        Long productId = product.getId();
        long ordersBefore = orderRepository.count();

        AddToCartRequest addRequest = new AddToCartRequest(productId, 1);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest))
                        .with(request -> {
                            request.setRemoteAddr("192.168.150.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andReturn();

        String cartToken = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("cartToken")
                .asText();
        String raceEmail = "doubleclick-" + UUID.randomUUID() + "@example.com";

        int threads = 2;
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger createdCount = new AtomicInteger();
        AtomicInteger clientErrorCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();

                        CheckoutRequest checkoutRequest = new CheckoutRequest(
                                "Double Click Buyer",
                                raceEmail,
                                "Rothschild 1, Tel Aviv"
                        );
                        MvcResult result = mockMvc.perform(post("/api/orders/checkout")
                                        .header("X-Cart-Token", cartToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(checkoutRequest))
                                        .with(request -> {
                                            request.setRemoteAddr("192.168.150." + (10 + index));
                                            return request;
                                        }))
                                .andReturn();

                        int status = result.getResponse().getStatus();
                        if (status == 201) {
                            createdCount.incrementAndGet();
                        } else if (status >= 400 && status < 500) {
                            clientErrorCount.incrementAndGet();
                        } else {
                            unexpectedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(createdCount.get())
                .as("Exactly one checkout must succeed")
                .isEqualTo(1);
        assertThat(clientErrorCount.get())
                .as("Duplicate concurrent checkout must fail with 4xx")
                .isEqualTo(1);
        assertThat(unexpectedCount.get()).isZero();
        assertThat(orderRepository.count())
                .as("Exactly one order must be persisted in DB")
                .isEqualTo(ordersBefore + 1);
        assertThat(productRepository.findById(productId).orElseThrow().getStockQuantity()).isEqualTo(9);
        assertThat(cartRepository.findByCartTokenWithItems(cartToken).orElseThrow().getItems())
                .as("Original cart must be empty after checkout")
                .isEmpty();
    }

    @Test
    @DisplayName("Concurrent payment requests on the same PENDING order settle at most once and reject duplicate with 409")
    void testConcurrentPaymentOnSameOrderSettlesOnce() throws Exception {
        Category category = categoryRepository.findById(1L)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name("Electronics")
                        .description("Test")
                        .build()));

        Product product = productRepository.saveAndFlush(Product.builder()
                .name("Concurrent Payment Item")
                .description("Payment race test")
                .price(new BigDecimal("75.00"))
                .stockQuantity(5)
                .category(category)
                .images(Collections.emptyList())
                .build());

        AddToCartRequest addRequest = new AddToCartRequest(product.getId(), 1);
        MvcResult addResult = mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest))
                        .with(request -> {
                            request.setRemoteAddr("192.168.160.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andReturn();

        String cartToken = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .get("cartToken")
                .asText();

        CheckoutRequest checkoutRequest = new CheckoutRequest(
                "Concurrent Payer",
                "payrace-" + UUID.randomUUID() + "@example.com",
                "Dizengoff 10, Tel Aviv"
        );
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("X-Cart-Token", cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest))
                        .with(request -> {
                            request.setRemoteAddr("192.168.160.2");
                            return request;
                        }))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode orderNode = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        long orderId = orderNode.get("id").asLong();
        String trackingNumber = orderNode.get("trackingNumber").asText();
        BigDecimal totalAmount = new BigDecimal(orderNode.get("totalAmount").asText());

        String futureExpiry = YearMonth.now().plusYears(2).format(DateTimeFormatter.ofPattern("MM/yy"));
        PaymentRequest paymentRequest = PaymentRequest.builder()
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .amount(totalAmount)
                .cardNumber("4111111111111111")
                .cardHolderName("Concurrent Payer")
                .expiryDate(futureExpiry)
                .cvv("123")
                .build();
        String paymentJson = objectMapper.writeValueAsString(paymentRequest);

        int threads = 2;
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();

                        MvcResult result = mockMvc.perform(post("/api/payments/orders/" + trackingNumber)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(paymentJson)
                                        .with(request -> {
                                            request.setRemoteAddr("192.168.160." + (10 + index));
                                            return request;
                                        }))
                                .andReturn();

                        int status = result.getResponse().getStatus();
                        if (status == 200) {
                            okCount.incrementAndGet();
                        } else if (status == 409) {
                            conflictCount.incrementAndGet();
                        } else {
                            unexpectedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpectedCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(okCount.get())
                .as("Exactly one payment must succeed")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("Concurrent duplicate payment must fail with 409 Conflict")
                .isEqualTo(1);
        assertThat(unexpectedCount.get()).isZero();

        Order settledOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(settledOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);
    }
}
