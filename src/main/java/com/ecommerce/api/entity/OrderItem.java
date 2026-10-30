package com.ecommerce.api.entity;

import com.ecommerce.api.constant.MoneyConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items", indexes = {@Index(name = "idx_order_item_order", columnList = "order_id"), @Index(name = "idx_order_item_product", columnList = "product_id")})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderItem {

    public static final int PRICE_PRECISION = MoneyConstants.PRECISION;
    public static final int PRICE_SCALE = MoneyConstants.SCALE;
    public static final int DEFAULT_QUANTITY = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = DEFAULT_QUANTITY;

    @Column(name = "price_at_purchase", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal priceAtPurchase;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public BigDecimal getSubtotal() {
        if (priceAtPurchase == null || quantity == null || quantity <= 0) {
            return BigDecimal.ZERO;
        }

        return priceAtPurchase.multiply(BigDecimal.valueOf(quantity));
    }
}
