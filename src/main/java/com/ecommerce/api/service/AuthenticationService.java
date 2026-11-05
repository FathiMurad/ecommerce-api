package com.ecommerce.api.service;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthenticationResponse;

/**
 * Service for user login and registration.
 */
public interface AuthenticationService {

    /**
     * Registers a new user and returns a token.
     */
    AuthenticationResponse register(RegisterRequest request);

    /**
     * Logs in a user and returns a token.
     */
    AuthenticationResponse authenticate(LoginRequest request);
}
