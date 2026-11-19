package com.ecommerce.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a standardized HTTP 401 JSON response when an unauthenticated user
 * attempts to access a protected endpoint.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";
    private static final String BEARER_CHALLENGE = "Bearer";

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException
    ) throws IOException {
        response.setHeader(WWW_AUTHENTICATE_HEADER, BEARER_CHALLENGE);
        JsonErrorWriter.write(
                response,
                HttpStatus.UNAUTHORIZED,
                "Full authentication is required to access this resource."
        );
    }
}
