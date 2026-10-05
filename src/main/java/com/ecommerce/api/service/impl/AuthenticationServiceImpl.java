package com.ecommerce.api.service.impl;

import com.ecommerce.api.dto.request.LoginRequest;
import com.ecommerce.api.dto.request.RegisterRequest;
import com.ecommerce.api.dto.response.AuthenticationResponse;
import com.ecommerce.api.entity.User;
import com.ecommerce.api.entity.enums.Role;
import com.ecommerce.api.repository.UserRepository;
import com.ecommerce.api.security.JwtService;
import com.ecommerce.api.security.SecurityUser;
import com.ecommerce.api.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the AuthenticationService.
 * Handles the business logic for registering new users and authenticating existing ones.
 */
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
        // Prevent duplicate emails
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email is already in use");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Note: Make sure the Role enum matches your actual enum values (e.g., CUSTOMER or USER)
        user.setRole(com.ecommerce.api.entity.enums.Role.ROLE_CUSTOMER);
        user.setIsActive(true);

        userRepository.save(user);

        SecurityUser securityUser = new SecurityUser(user);
        String jwtToken = jwtService.generateToken(securityUser);

        return new AuthenticationResponse(jwtToken);
    }

    @Override
    public AuthenticationResponse authenticate(LoginRequest request) {
        // Authenticate the user credentials using Spring Security's AuthenticationManager
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        // If execution reaches here, authentication was successful
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(() -> new IllegalArgumentException("User not found"));

        SecurityUser securityUser = new SecurityUser(user);
        String jwtToken = jwtService.generateToken(securityUser);

        return new AuthenticationResponse(jwtToken);
    }
}
