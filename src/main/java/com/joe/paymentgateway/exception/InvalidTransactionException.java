package com.joe.paymentgateway.exception;

import java.util.List;

/**
 * Thrown when an incoming transaction request fails validation.
 *
 * <p>Contains a list of specific validation error messages describing
 * which ISO 8583 fields are missing or malformed.</p>
 */
public class InvalidTransactionException extends RuntimeException {

    private final List<String> validationErrors;

    public InvalidTransactionException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }

    /**
     * Returns the list of field-level validation errors.
     *
     * @return immutable list of error message strings
     */
    public List<String> getValidationErrors() {
        return List.copyOf(validationErrors);
    }
}
