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
    @Size(max = User.FIRST_NAME_MAX_LENGTH, message = "First name must not exceed {max} characters")
    private String firstName;

    /**
     * The last name of the user.
     */
    @NotBlank(message = "Last name is required")
    @Size(max = User.LAST_NAME_MAX_LENGTH, message = "Last name must not exceed {max} characters")
    private String lastName;

    /**
     * The email address of the user, which also serves as the username.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is not valid")
    @Size(max = User.EMAIL_MAX_LENGTH, message = "Email must not exceed {max} characters")
    private String email;

    /**
     * The password for the new account.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = User.PASSWORD_MAX_LENGTH, message = "Password must be between {min} and {max} characters")
    private String password;
}
