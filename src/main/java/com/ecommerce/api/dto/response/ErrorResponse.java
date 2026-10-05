package com.ecommerce.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard API error response payload returned to clients upon exceptions.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * HTTP status code (e.g., 400, 401, 404).
     */
    private int status;

    /**
     * Human-readable summary of the error.
     */
    private String message;

    /**
     * Exact timestamp when the error occurred.
     */
    private LocalDateTime timestamp;

    /**
     * Field-specific validation error details, mapped as fieldName -> errorMessage.
     */
    private Map<String, String> validationErrors;
}
