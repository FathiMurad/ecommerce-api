package com.ecommerce.api.entity;

import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.entity.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Entity representing an order placed by a customer.
 */
@Entity
@Table(name = "orders", indexes = {@Index(name = "idx_order_tracking_number", columnList = "tracking_number", unique = true), @Index(name = "idx_order_customer_email", columnList = "customer_email"), @Index(name = "idx_order_user_id", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    public static final int TRACKING_NUMBER_MAX_LENGTH = 64;
    public static final int CUSTOMER_EMAIL_MAX_LENGTH = 150;
    public static final int CUSTOMER_NAME_MAX_LENGTH = 100;
    public static final int SHIPPING_ADDRESS_MAX_LENGTH = 255;
    public static final int STATUS_MAX_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = TRACKING_NUMBER_MAX_LENGTH)
    private String trackingNumber;

    /**
     * Authenticated user owning this order. Nullable for guest checkouts.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "customer_email", nullable = false, length = CUSTOMER_EMAIL_MAX_LENGTH)
    private String customerEmail;

    @Column(name = "customer_name", nullable = false, length = CUSTOMER_NAME_MAX_LENGTH)
    private String customerName;

    @Column(name = "shipping_address", nullable = false, length = SHIPPING_ADDRESS_MAX_LENGTH)
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = STATUS_MAX_LENGTH)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = MoneyConstants.PRECISION, scale = MoneyConstants.SCALE)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Helper method to attach order items maintaining bidirectional integrity.
     */
    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);
    }

    /**
     * Calculates the order total from all line items.
     */
    public BigDecimal calculateTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : this.items) {
            total = total.add(item.getSubtotal());
        }
        return total;
    }
}
