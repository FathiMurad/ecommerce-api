package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthenticationResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.exception.BadRequestException;
import com.ecommerce.api.exception.ResourceNotFoundException;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.security.JwtService;
import com.ecommerce.api.security.SecurityUser;
import com.ecommerce.api.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the AuthenticationService.
 * Handles the business logic for registering new users and authenticating existing ones
 * with email normalization, existence checks, and account status enforcement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Register request cannot be null.");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required for registration.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Prevent duplicate emails using lightweight SQL EXISTS
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("Email is already in use: " + normalizedEmail);
        }

        User user = User.builder().firstName(request.getFirstName() != null ? request.getFirstName().trim() : null).lastName(request.getLastName() != null ? request.getLastName().trim() : null).email(normalizedEmail).password(passwordEncoder.encode(request.getPassword())).role(Role.ROLE_CUSTOMER).isActive(true).build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user id={} with email={}", savedUser.getId(), savedUser.getEmail());

        SecurityUser securityUser = new SecurityUser(savedUser);
        String jwtToken = jwtService.generateToken(securityUser);

        return new AuthenticationResponse(jwtToken);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticationResponse authenticate(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request cannot be null.");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required for login.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Authenticate credentials using Spring Security's AuthenticationManager
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword()));

        User user = userRepository.findByEmail(normalizedEmail).orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        if (!user.isActive()) {
            log.warn("Login attempt for deactivated user email={}", normalizedEmail);
            throw new BadRequestException("User account is inactive. Please contact support.");
        }

        log.info("User authenticated successfully: email={}", normalizedEmail);

        SecurityUser securityUser = new SecurityUser(user);
        String jwtToken = jwtService.generateToken(securityUser);

        return new AuthenticationResponse(jwtToken);
    }
}
