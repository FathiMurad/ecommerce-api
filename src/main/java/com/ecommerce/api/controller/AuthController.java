package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthenticationResponse;
import com.ecommerce.api.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing user authentication and registration.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Registers a new user in the system and returns a JWT token.
     *
     * @param request the registration details provided by the user
     * @return ResponseEntity containing the AuthenticationResponse with the token
     */
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthenticationResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates an existing user and returns a JWT token.
     *
     * @param request the login credentials provided by the user
     * @return ResponseEntity containing the AuthenticationResponse with the token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> authenticate(@Valid @RequestBody LoginRequest request) {
        AuthenticationResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }
}
