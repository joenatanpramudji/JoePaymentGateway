package com.joe.paymentgateway.exception;

/**
 * Thrown when a transaction lookup fails to find a matching record.
 *
 * <p>Typically occurs when a void request references an RRN or transaction ID
 * that does not exist in the database.</p>
 */
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String message) {
        super(message);
    }
}
