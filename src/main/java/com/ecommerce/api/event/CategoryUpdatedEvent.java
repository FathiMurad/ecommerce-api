package com.ecommerce.api.event;

/**
 * Domain event published when a catalog category is modified.
 */
public record CategoryUpdatedEvent(Long categoryId) {
}
