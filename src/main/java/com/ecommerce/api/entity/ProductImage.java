package com.ecommerce.api.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

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

    @Column(nullable = false, length = IMAGE_URL_MAX_LENGTH)
    @Comment("Direct URL pointing to the image asset stored in object storage (e.g., S3/CDN)")
    private String imageUrl;

    @Column(nullable = false)
    @Comment("Indicates whether this image is the main display thumbnail for product listings")
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(nullable = false)
    @Comment("Display sequence index for frontend carousel or thumbnail grid")
    @Builder.Default
    private Integer displayOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
