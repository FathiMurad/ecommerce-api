package com.ecommerce.api.event;

/**
 * Domain event published when a catalog category is updated.
 */
public record CategoryUpdatedEvent(Long categoryId) {
}
