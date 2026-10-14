package com.ecommerce.api.entity;

import com.ecommerce.api.constant.MoneyConstants;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Core product entity representing an item available for sale.
 * Supports optimistic concurrency control and multiple product images.
 */
@Entity
@Table(name = "products", indexes = {@Index(name = "idx_product_category", columnList = "category_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    public static final int NAME_MAX_LENGTH = 150;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    public static final int PRICE_PRECISION = MoneyConstants.PRECISION;
    public static final int PRICE_SCALE = MoneyConstants.SCALE;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    @Comment("Detailed product description limited to 2000 characters to prevent resource exhaustion")
    private String description;

    @Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    @Comment("Monetary unit stored as exact decimal to avoid floating-point rounding issues")
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    /**
     * Managed by JPA for optimistic locking.
     * Prevents race conditions during concurrent checkout transactions.
     */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    /**
     * Helper method to maintain bidirectional relationship consistency.
     */
    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    /**
     * Helper method to safely remove an image from the product.
     */
    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setProduct(null);
    }
}
