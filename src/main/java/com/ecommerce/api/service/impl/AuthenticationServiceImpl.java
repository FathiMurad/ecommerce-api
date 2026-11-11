package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthenticationResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.exception.ConflictException;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.security.JwtService;
import com.ecommerce.api.security.SecurityUser;
import com.ecommerce.api.service.AuthenticationService;
import com.ecommerce.api.util.Emails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for user login and registration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String normalizedEmail = Emails.normalize(request.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration rejected: Duplicate email conflict for requested account");
            throw new ConflictException("Registration could not be completed with the provided details.");
        }

        User user = User.builder().firstName(request.getFirstName() != null ? request.getFirstName().trim() : "").lastName(request.getLastName() != null ? request.getLastName().trim() : "").email(normalizedEmail).password(passwordEncoder.encode(request.getPassword())).role(Role.ROLE_CUSTOMER).isActive(true).build();

        try {
            User savedUser = userRepository.saveAndFlush(user);
            log.info("Registered new user id={} with email={}", savedUser.getId(), savedUser.getEmail());

            SecurityUser securityUser = new SecurityUser(savedUser);
            String token = jwtService.generateToken(securityUser);

            return new AuthenticationResponse(token);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate registration collision on email");
            throw new ConflictException("Registration could not be completed with the provided details.");
        }
    }

    @Override
    public AuthenticationResponse authenticate(LoginRequest request) {
        validateLoginRequest(request);

        String normalizedEmail = Emails.normalize(request.getEmail());

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword()));

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        String token = jwtService.generateToken(securityUser);
        log.info("User authenticated successfully: email={}", normalizedEmail);

        return new AuthenticationResponse(token);
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Registration request cannot be null.");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request cannot be null.");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
    }
}
