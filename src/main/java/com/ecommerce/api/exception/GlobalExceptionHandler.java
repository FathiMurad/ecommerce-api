package com.ecommerce.api.exception;

import com.ecommerce.api.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@SuppressWarnings("unused")
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("Endpoint path not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "The requested endpoint or resource does not exist.");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException ex) {
        log.warn("Conflict detected: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("Database constraint or unique key violation encountered: {}", ex.getClass().getSimpleName());
        return buildResponse(HttpStatus.CONFLICT, "The request conflicts with existing records or database constraints.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        log.warn("Concurrent update collision: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "The record was updated concurrently by another operation. Please retry.");
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStockException(InsufficientStockException ex) {
        log.warn("Insufficient stock rejected: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<ErrorResponse> handleEmptyCartException(EmptyCartException ex) {
        log.warn("Empty cart operation rejected: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class, InvalidDataAccessApiUsageException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequestParams(Exception ex) {
        log.warn("Invalid request parameters or sorting arguments: {}", ex.getMessage());
        String clientMessage = (ex instanceof InvalidDataAccessApiUsageException || ex instanceof IllegalArgumentException) ? "Invalid sort parameter or query field specified." : "Required request parameter or header is missing.";
        return buildResponse(HttpStatus.BAD_REQUEST, clientMessage);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder().status(HttpStatus.BAD_REQUEST.value()).error(HttpStatus.BAD_REQUEST.getReasonPhrase()).message("Input validation failed for one or more fields.").timestamp(LocalDateTime.now()).validationErrors(fieldErrors).build();

        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Malformed HTTP request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed JSON request body or invalid field format.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' should be of type '%s'", ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("Type mismatch: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {}", ex.getMessage());
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException ex) {
        log.warn("Media type not acceptable: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_ACCEPTABLE, ex.getMessage());
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class, LockedException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication rejected: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAnonymous = auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated();

        if (isAnonymous) {
            log.warn("Unauthenticated access attempt intercepted: {}", ex.getMessage());
            return buildResponse(HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource.");
        }

        log.warn("Access denied for user={}: {}", auth.getName(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied: You do not possess the required permissions.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unhandled internal server exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal server error occurred. Please contact support.");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse errorResponse = ErrorResponse.builder().status(status.value()).error(status.getReasonPhrase()).message(message).timestamp(LocalDateTime.now()).build();
        return ResponseEntity.status(status).body(errorResponse);
    }
}
