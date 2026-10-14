package com.ecommerce.api.dto.request;

import com.ecommerce.api.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating or updating a product category.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Category name is mandatory")
    @Size(max = Category.NAME_MAX_LENGTH, message = "Category name must not exceed {max} characters")
    private String name;

    @Size(max = Category.DESCRIPTION_MAX_LENGTH, message = "Description must not exceed {max} characters")
    private String description;
}
