package com.ecommerce.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing the response after successful authentication.
 * Contains the generated JWT token to be used for subsequent secured HTTP requests.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {

    /**
     * The JSON Web Token (JWT) issued to the authenticated user.
     */
    private String token;
}
