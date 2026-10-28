package com.ecommerce.api.dto.request;

import com.ecommerce.api.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a user registration request.
 * Contains the necessary validation rules and payload to register a new account.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    public static final int RAW_PASSWORD_MIN_LENGTH = 6;

    /**
     * Maximum character length for raw user passwords.
     * Capped at 72 to strictly align with BCrypt's internal 72-byte truncation limit,
     * preventing silent password truncation security flaws and long-string hashing DoS attacks.
     */
    public static final int RAW_PASSWORD_MAX_LENGTH = 72;

    @NotBlank(message = "First name is required")
    @Size(max = User.FIRST_NAME_MAX_LENGTH, message = "First name must not exceed {max} characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = User.LAST_NAME_MAX_LENGTH, message = "Last name must not exceed {max} characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is not valid")
    @Size(max = User.EMAIL_MAX_LENGTH, message = "Email must not exceed {max} characters")
    private String email;

    /**
     * The raw plaintext password for the new account.
     * Enforces minimum length and complies with BCrypt's 72-byte limit.
     */
    @NotBlank(message = "Password is required")
    @Size(min = RAW_PASSWORD_MIN_LENGTH, max = RAW_PASSWORD_MAX_LENGTH, message = "Password must be between {min} and {max} characters")
    private String password;
}
