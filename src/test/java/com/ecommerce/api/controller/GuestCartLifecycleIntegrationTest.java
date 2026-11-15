package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.AddToCartRequest;
import com.ecommerce.api.repository.CartRepository;
import com.ecommerce.api.scheduler.GuestCartCleanupJob;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestCartLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private GuestCartCleanupJob guestCartCleanupJob;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Anonymous GET /api/cart without token returns empty cart without inserting into DB")
    void testAnonymousGetCartWithoutTokenDoesNotInsertDatabaseRow() throws Exception {
        long cartsBefore = cartRepository.count();

        mockMvc.perform(get("/api/cart")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(nullValue())).andExpect(jsonPath("$.cartToken").value(nullValue())).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalAmount").value(0.00)).andExpect(jsonPath("$.totalItems").value(0));

        assertThat(cartRepository.count()).isEqualTo(cartsBefore);
    }

    @Test
    @DisplayName("Anonymous removeCartItem and clearCart without existing cart do not insert into DB")
    void testAnonymousRemoveAndClearWithoutCartDoNotInsertDatabaseRow() throws Exception {
        long cartsBefore = cartRepository.count();

        mockMvc.perform(delete("/api/cart/items/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(nullValue())).andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(delete("/api/cart")).andExpect(status().isNoContent());

        assertThat(cartRepository.count()).isEqualTo(cartsBefore);
    }

    @Test
    @DisplayName("GuestCartCleanupJob deletes abandoned guest carts older than 30 days while preserving active ones")
    void testCleanupAbandonedGuestCartsDeletesOnlyExpiredCarts() throws Exception {
        AddToCartRequest addRequest = new AddToCartRequest(1L, 1);

        // 1. Create an old guest cart (31 days inactive)
        MvcResult oldCartResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andReturn();
        String oldCartToken = objectMapper.readTree(oldCartResult.getResponse().getContentAsString()).get("cartToken").asText();

        // 2. Create a recent guest cart (active today)
        MvcResult recentCartResult = mockMvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(addRequest))).andExpect(status().isOk()).andReturn();
        String recentCartToken = objectMapper.readTree(recentCartResult.getResponse().getContentAsString()).get("cartToken").asText();

        // Backdate updated_at of the old cart to 31 days ago
        entityManager.flush();
        entityManager.createQuery("UPDATE Cart c SET c.updatedAt = :expiredAt WHERE c.cartToken = :token").setParameter("expiredAt", LocalDateTime.now().minusDays(31)).setParameter("token", oldCartToken).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // 3. Run scheduled cleanup job
        guestCartCleanupJob.cleanupAbandonedGuestCarts();
        entityManager.flush();
        entityManager.clear();

        // 4. Verify expired cart was deleted and active cart remains
        assertThat(cartRepository.findByCartTokenWithItems(oldCartToken)).isEmpty();
        assertThat(cartRepository.findByCartTokenWithItems(recentCartToken)).isPresent();
    }
}
