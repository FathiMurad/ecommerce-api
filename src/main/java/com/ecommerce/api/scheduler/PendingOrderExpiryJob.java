package com.ecommerce.api.scheduler;

import com.ecommerce.api.entity.Order;
import com.ecommerce.api.entity.enums.OrderStatus;
import com.ecommerce.api.repository.OrderRepository;
import com.ecommerce.api.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Periodically cancels stale pending orders to release reserved product inventory back to the catalog.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderExpiryJob {

    private static final int EXPIRY_MINUTES = 30;
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(
            initialDelayString = "${app.scheduler.pending-order-expiry.initial-delay:PT1M}",
            fixedDelayString = "${app.scheduler.pending-order-expiry.fixed-delay:PT5M}"
    )
    public void cancelExpiredPendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(EXPIRY_MINUTES);
        Page<Order> expiredPage = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING,
                cutoff,
                PageRequest.of(0, BATCH_SIZE)
        );

        int cancelledCount = 0;
        for (Order order : expiredPage.getContent()) {
            try {
                if (orderService.cancelIfPending(order.getTrackingNumber())) {
                    cancelledCount++;
                    log.warn("Cancelled expired PENDING order trackingNumber={} created at {}", order.getTrackingNumber(), order.getCreatedAt());
                }
            } catch (Exception ex) {
                log.error("Failed to cancel expired PENDING order trackingNumber={}: {}", order.getTrackingNumber(), ex.getMessage(), ex);
            }
        }

        if (cancelledCount > 0) {
            log.info("PendingOrderExpiryJob cancelled {} expired PENDING orders", cancelledCount);
        }
    }
}
