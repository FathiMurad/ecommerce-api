package com.ecommerce.api.controller;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthenticationResponse;
import com.ecommerce.api.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing user authentication and registration workflows.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration and JWT authentication")
class AuthController {

    private final AuthenticationService authenticationService;

    @Operation(summary = "Register a new customer", description = "Creates a new customer account and returns a signed JWT token.")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Customer successfully registered and authenticated", content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))), @ApiResponse(responseCode = "400", description = "Invalid input data or malformed request payload", content = @Content), @ApiResponse(responseCode = "409", description = "An account with the provided email already exists", content = @Content), @ApiResponse(responseCode = "429", description = "Too many authentication requests - rate limit exceeded", content = @Content)})
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthenticationResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Authenticate user credentials", description = "Validates user credentials and issues a signed JWT token for authorized API access.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User successfully authenticated", content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))), @ApiResponse(responseCode = "400", description = "Invalid input data or malformed request payload", content = @Content), @ApiResponse(responseCode = "401", description = "Invalid credentials or inactive account", content = @Content), @ApiResponse(responseCode = "429", description = "Too many authentication requests - rate limit exceeded", content = @Content)})
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> authenticate(@Valid @RequestBody LoginRequest request) {
        AuthenticationResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(response);
    }
}
