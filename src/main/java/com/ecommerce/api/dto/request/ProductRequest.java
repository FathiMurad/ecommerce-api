package com.ecommerce.api.dto.request;

import com.ecommerce.api.constant.MoneyConstants;
import com.ecommerce.api.entity.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload for creating or updating a catalog product.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Product name is mandatory")
    @Size(max = Product.NAME_MAX_LENGTH, message = "Product name must not exceed {max} characters")
    private String name;

    @Size(max = Product.DESCRIPTION_MAX_LENGTH, message = "Description must not exceed {max} characters")
    private String description;

    @NotNull(message = "Price is mandatory")
    @DecimalMin(value = "0.01", message = "Price must be strictly positive")
    @Digits(integer = MoneyConstants.PRECISION - MoneyConstants.SCALE, fraction = MoneyConstants.SCALE, message = "Price cannot exceed 10 integer digits and 2 decimal places")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is mandatory")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    @NotNull(message = "Category ID is mandatory")
    private Long categoryId;

    private List<String> imageUrls;
}
