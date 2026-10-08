package com.ecommerce.api.dto.request;

import com.ecommerce.api.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for administrative order status transitions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    @NotNull(message = "Order status is mandatory")
    private OrderStatus status;
}
