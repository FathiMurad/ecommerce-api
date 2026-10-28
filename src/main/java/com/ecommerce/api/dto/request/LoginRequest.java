package com.ecommerce.api.dto.request;

import com.ecommerce.api.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Data Transfer Object representing a user login request.
 * Contains credentials required for authenticating an existing user securely.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class LoginRequest {

    public static final int PASSWORD_MAX_LENGTH = 100;

    /**
     * The email address used during registration.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is not valid")
    @Size(max = User.EMAIL_MAX_LENGTH, message = "Email must not exceed {max} characters")
    private String email;

    /**
     * The raw password provided by the user for authentication.
     */
    @NotBlank(message = "Password is required")
    @Size(max = PASSWORD_MAX_LENGTH, message = "Password must not exceed {max} characters")
    private String password;
}
