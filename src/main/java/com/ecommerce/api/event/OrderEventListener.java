package com.ecommerce.api.event;

import com.ecommerce.api.config.AsyncConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous event consumer decoupling secondary side effects (notifications, receipts, audit logs)
 * from the synchronous checkout database transaction.
 */
@Slf4j
@Getter
@Component
public class OrderEventListener {

    private final AtomicInteger processedEventCount = new AtomicInteger(0);
    private volatile OrderPlacedEvent lastProcessedEvent;
    private volatile String executionThreadName;
    private volatile CountDownLatch latch = new CountDownLatch(1);

    @Async(AsyncConfig.ORDER_ASYNC_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        this.executionThreadName = Thread.currentThread().getName();
        this.lastProcessedEvent = event;
        this.processedEventCount.incrementAndGet();

        log.info("Processing OrderPlacedEvent asynchronously on thread [{}]: trackingNumber={}, email={}, totalAmount={}", executionThreadName, event.trackingNumber(), event.customerEmail(), event.totalAmount());

        simulateEmailNotification(event);
        simulateAuditLogging(event);

        if (latch != null) {
            latch.countDown();
        }
    }

    public int getProcessedEventCount() {
        return processedEventCount.get();
    }

    public void reset(int expectedCount) {
        processedEventCount.set(0);
        lastProcessedEvent = null;
        executionThreadName = null;
        latch = new CountDownLatch(expectedCount);
    }

    private void simulateEmailNotification(OrderPlacedEvent event) {
        log.info("Order confirmation email sent to {} for trackingNumber={}", event.customerEmail(), event.trackingNumber());
    }

    private void simulateAuditLogging(OrderPlacedEvent event) {
        log.info("Financial audit entry recorded for orderId={}, trackingNumber={}, amount={}", event.orderId(), event.trackingNumber(), event.totalAmount());
    }
}
