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

import java.util.Objects;

/**
 * Service implementation for user login and registration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final String REGISTRATION_CONFLICT_MESSAGE = "Registration could not be completed with the provided details.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        Objects.requireNonNull(request, "Registration request cannot be null.");

        String normalizedEmail = Emails.normalize(request.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration rejected: Duplicate email conflict for requested account");
            throw new ConflictException(REGISTRATION_CONFLICT_MESSAGE);
        }

        User user = User.builder().firstName(trimToEmpty(request.getFirstName())).lastName(trimToEmpty(request.getLastName())).email(normalizedEmail).password(passwordEncoder.encode(request.getPassword())).role(Role.ROLE_CUSTOMER).isActive(true).build();

        try {
            User savedUser = userRepository.saveAndFlush(user);
            log.info("Registered new user id={}", savedUser.getId());
            return issueToken(savedUser);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate registration collision");
            throw new ConflictException(REGISTRATION_CONFLICT_MESSAGE);
        }
    }

    @Override
    public AuthenticationResponse authenticate(LoginRequest request) {
        Objects.requireNonNull(request, "Login request cannot be null.");

        String normalizedEmail = Emails.normalize(request.getEmail());

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword()));

        if (!(authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            throw new IllegalStateException("Unexpected authentication principal.");
        }

        User authenticatedUser = securityUser.getUser();
        log.info("User authenticated successfully: userId={}", authenticatedUser.getId());

        return issueToken(authenticatedUser);
    }

    private AuthenticationResponse issueToken(User user) {
        String token = jwtService.generateToken(new SecurityUser(user));
        return new AuthenticationResponse(token);
    }

    private String trimToEmpty(String value) {
        return value != null ? value.trim() : "";
    }
}
