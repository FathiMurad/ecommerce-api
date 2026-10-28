package com.ecommerce.api.entity;

import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.entity.enums.PaymentMethod;
import com.ecommerce.api.entity.enums.PaymentStatus;
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
import jakarta.persistence.OneToOne;
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

/**
 * Entity representing an individual payment transaction linked to an Order.
 * Tracks gateway transaction status, payment method, and failure diagnostics.
 */
@Entity
@Table(name = "payments", indexes = {@Index(name = "idx_payment_transaction_id", columnList = "transaction_id", unique = true), @Index(name = "idx_payment_order_id", columnList = "order_id", unique = true), @Index(name = "idx_payment_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment {

    public static final int TRANSACTION_ID_MAX_LENGTH = 100;
    public static final int STATUS_MAX_LENGTH = 30;
    public static final int PAYMENT_METHOD_MAX_LENGTH = 30;
    public static final int FAILURE_REASON_MAX_LENGTH = 500;
    public static final int AMOUNT_PRECISION = MoneyConstants.PRECISION;
    public static final int AMOUNT_SCALE = MoneyConstants.SCALE;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "transaction_id", nullable = false, unique = true, length = TRANSACTION_ID_MAX_LENGTH)
    private String transactionId;

    @Column(nullable = false, precision = AMOUNT_PRECISION, scale = AMOUNT_SCALE)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = STATUS_MAX_LENGTH)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = PAYMENT_METHOD_MAX_LENGTH)
    private PaymentMethod paymentMethod;

    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
