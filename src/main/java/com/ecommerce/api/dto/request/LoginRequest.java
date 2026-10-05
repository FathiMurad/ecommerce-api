package com.ecommerce.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a user login request.
 * Contains the credentials required to authenticate an existing user.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    /**
     * The email address used during registration.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is not valid")
    private String email;

    /**
     * The raw password provided by the user for authentication.
     */
    @NotBlank(message = "Password is required")
    private String password;
}
