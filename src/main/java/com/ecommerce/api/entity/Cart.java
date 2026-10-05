package com.ecommerce.api.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a shopping cart containing multiple cart items.
 */
@Entity
@Table(name = "carts", indexes = {@Index(name = "idx_cart_token", columnList = "cart_token", unique = true)})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_token", nullable = false, unique = true, length = 64)
    private String cartToken;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Adds an item to the cart or increments its quantity if it already exists.
     *
     * @param product  The product to add.
     * @param quantity The quantity to add.
     */
    public void addProduct(Product product, int quantity) {
        CartItem existingItem = null;

        for (CartItem item : this.items) {
            if (item.getProduct().getId().equals(product.getId())) {
                existingItem = item;
                break;
            }
        }

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
        } else {
            CartItem newItem = CartItem.builder().cart(this).product(product).quantity(quantity).build();
            this.items.add(newItem);
        }
    }

    /**
     * Removes an item completely from the cart by product id.
     *
     * @param productId The ID of the product to remove.
     */
    public void removeProduct(Long productId) {
        CartItem itemToRemove = null;

        for (CartItem item : this.items) {
            if (item.getProduct().getId().equals(productId)) {
                itemToRemove = item;
                break;
            }
        }

        if (itemToRemove != null) {
            this.items.remove(itemToRemove);
            itemToRemove.setCart(null);
        }
    }

    /**
     * Calculates the total amount for the entire cart.
     *
     * @return Total price as BigDecimal.
     */
    public BigDecimal calculateTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : this.items) {
            total = total.add(item.getSubtotal());
        }

        return total;
    }

    /**
     * Clears all items from the cart.
     */
    public void clear() {
        for (CartItem item : this.items) {
            item.setCart(null);
        }
        this.items.clear();
    }
}
