package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthenticationResponse;

/**
 * Service interface for handling user registration and authentication logic.
 */
public interface AuthenticationService {

    /**
     * Registers a new user in the system and returns a JWT token.
     *
     * @param request the registration details containing first name, last name, email, and password
     * @return an AuthenticationResponse containing the generated JWT token
     */
    AuthenticationResponse register(RegisterRequest request);

    /**
     * Authenticates an existing user and returns a JWT token upon success.
     *
     * @param request the login credentials containing email and password
     * @return an AuthenticationResponse containing the generated JWT token
     */
    AuthenticationResponse authenticate(LoginRequest request);
}
