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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_images", indexes = {@Index(name = "idx_product_image_product", columnList = "product_id")})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductImage {

    public static final int IMAGE_URL_MAX_LENGTH = 500;
    public static final int DEFAULT_DISPLAY_ORDER = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean isPrimary = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer displayOrder = DEFAULT_DISPLAY_ORDER;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Required for Jackson and reflection mappers.
     * Lombok generates 'isPrimary()', which breaks frameworks expecting JavaBean 'getIsPrimary()'.
     */
    public boolean getIsPrimary() {
        return this.isPrimary;
    }
}
