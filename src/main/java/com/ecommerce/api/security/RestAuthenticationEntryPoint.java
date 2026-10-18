package com.ecommerce.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        JsonErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource.");
    }
}
