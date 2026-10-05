package com.ecommerce.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a user registration request.
 * Contains the necessary information to create a new user account.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    /**
     * The first name of the user.
     */
    @NotBlank(message = "First name is required")
    private String firstName;

    /**
     * The last name of the user.
     */
    @NotBlank(message = "Last name is required")
    private String lastName;

    /**
     * The email address of the user, which also serves as the username.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is not valid")
    private String email;

    /**
     * The password for the new account.
     * Must be at least 6 characters long to meet basic security requirements.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String password;
}
