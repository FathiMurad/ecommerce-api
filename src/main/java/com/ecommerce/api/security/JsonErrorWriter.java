package com.ecommerce.api.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility to write clean, strongly-typed JSON error responses directly to the HttpServletResponse,
 * completely avoiding ObjectMapper serialization discrepancies or missing Jackson modules.
 */
public final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String json = String.format("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}", status.value(), status.getReasonPhrase(), message, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        response.getWriter().write(json);
    }
}
