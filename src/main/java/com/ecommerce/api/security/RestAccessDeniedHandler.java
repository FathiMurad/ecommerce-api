package com.ecommerce.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull AccessDeniedException accessDeniedException) throws IOException {
        JsonErrorWriter.write(response, HttpStatus.FORBIDDEN, "Access denied: You do not possess the required permissions.");
    }
}
