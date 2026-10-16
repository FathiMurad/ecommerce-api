package com.ecommerce.api.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a single image asset associated with a product.
 * Supports primary thumbnail designation and gallery display ordering.
 */
@Entity
@Table(name = "product_images", indexes = {@Index(name = "idx_product_image_product", columnList = "product_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    public static final int IMAGE_URL_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = IMAGE_URL_MAX_LENGTH, comment = "Direct URL pointing to the image asset stored in object storage (e.g., S3/CDN)")
    private String imageUrl;

    @Column(nullable = false, comment = "Indicates whether this image is the main display thumbnail for product listings")
    @Builder.Default
    private boolean isPrimary = false;

    @Column(nullable = false, comment = "Display sequence index for frontend carousel or thumbnail grid")
    @Builder.Default
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Backward-compatible getter alias for serialization and mapping layers.
     */
    public boolean getIsPrimary() {
        return this.isPrimary;
    }

    /**
     * Backward-compatible setter alias for mapping layers.
     */
    public void setIsPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
    }
}
