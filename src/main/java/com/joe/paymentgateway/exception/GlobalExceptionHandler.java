package com.joe.paymentgateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized exception handler for all REST controllers.
 *
 * <p>Translates application exceptions into consistent JSON error responses
 * with appropriate HTTP status codes. Prevents stack traces and internal
 * details from leaking to API consumers.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles validation failures on ISO 8583 fields.
     * Returns HTTP 400 with a list of specific field errors.
     */
    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransaction(InvalidTransactionException ex) {
        log.warn("Transaction validation failed: {}", ex.getMessage());
        Map<String, Object> body = buildErrorBody(
                HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getValidationErrors());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles cases where a referenced transaction cannot be found.
     * Returns HTTP 404.
     */
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionNotFound(TransactionNotFoundException ex) {
        log.warn("Transaction not found: {}", ex.getMessage());
        Map<String, Object> body = buildErrorBody(HttpStatus.NOT_FOUND, ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles cryptographic operation failures.
     * Returns HTTP 500 without exposing internal crypto details.
     */
    @ExceptionHandler(CryptoException.class)
    public ResponseEntity<Map<String, Object>> handleCryptoException(CryptoException ex) {
        log.error("Cryptographic operation failed: {}", ex.getMessage(), ex);
        Map<String, Object> body = buildErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR, "Encryption error occurred", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Handles Spring validation errors from @Valid annotations on request DTOs.
     * Returns HTTP 400 with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        log.warn("Request validation failed: {}", fieldErrors);
        Map<String, Object> body = buildErrorBody(
                HttpStatus.BAD_REQUEST, "Request validation failed", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Returns HTTP 500 without exposing internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        Map<String, Object> body = buildErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR, "An internal error occurred", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Builds a standardized error response body.
     */
    private Map<String, Object> buildErrorBody(HttpStatus status, String message, List<String> details) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        return body;
    }
}
