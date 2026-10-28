package com.ecommerce.api.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
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
import java.util.UUID;

/**
 * Entity representing a shopping cart containing multiple cart items.
 * Can be associated with an authenticated User or exist anonymously via cartToken.
 */
@Entity
@Table(name = "carts", indexes = {@Index(name = "idx_cart_token", columnList = "cart_token", unique = true), @Index(name = "idx_cart_user_id", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Cart {

    public static final int CART_TOKEN_MAX_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_token", nullable = false, unique = true, length = CART_TOKEN_MAX_LENGTH)
    private String cartToken;

    /**
     * The authenticated user owning this cart. Null for anonymous/guest sessions.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
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

    @PrePersist
    protected void onCreate() {
        if (this.cartToken == null || this.cartToken.isBlank()) {
            this.cartToken = UUID.randomUUID().toString();
        }
    }

    /**
     * Adds an item to the cart or increments its quantity if it already exists.
     * Safely ignores invalid input to prevent runtime exceptions.
     */
    public void addProduct(Product product, int quantity) {
        if (product == null || product.getId() == null || quantity <= 0) {
            return;
        }

        CartItem existingItem = null;
        for (CartItem item : this.items) {
            if (item.getProduct() != null && product.getId().equals(item.getProduct().getId())) {
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
     * Safely handles null checks to prevent runtime exceptions.
     */
    public void removeProduct(Long productId) {
        if (productId == null) {
            return;
        }

        CartItem itemToRemove = null;
        for (CartItem item : this.items) {
            if (item.getProduct() != null && productId.equals(item.getProduct().getId())) {
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
     * Calculates the total amount for the entire cart safely without rounding errors.
     */
    public BigDecimal calculateTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : this.items) {
            if (item != null && item.getSubtotal() != null) {
                total = total.add(item.getSubtotal());
            }
        }

        return total;
    }

    /**
     * Clears all items from the cart and disassociates the bidirectional link.
     */
    public void clear() {
        for (CartItem item : this.items) {
            if (item != null) {
                item.setCart(null);
            }
        }
        this.items.clear();
    }
}
