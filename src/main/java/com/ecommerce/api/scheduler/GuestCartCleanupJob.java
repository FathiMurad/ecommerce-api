package com.ecommerce.api.scheduler;

import com.ecommerce.api.entity.Cart;
import com.ecommerce.api.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Daily maintenance job that removes abandoned anonymous guest carts
 * inactive for more than 30 days using JPA entity deletion to preserve cascade cleanup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestCartCleanupJob {

    private static final int RETENTION_DAYS = 30;
    private static final int BATCH_SIZE = 100;

    private final CartRepository cartRepository;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupAbandonedGuestCarts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deletedCount = 0;

        while (true) {
            Page<Cart> expiredBatch = cartRepository.findByUserIsNullAndUpdatedAtBefore(cutoff, PageRequest.of(0, BATCH_SIZE));
            if (expiredBatch.isEmpty()) {
                break;
            }
            cartRepository.deleteAll(expiredBatch.getContent());
            deletedCount += expiredBatch.getNumberOfElements();
        }

        if (deletedCount > 0) {
            log.info("GuestCartCleanupJob removed {} abandoned guest carts older than {} days", deletedCount, RETENTION_DAYS);
        }
    }
}
