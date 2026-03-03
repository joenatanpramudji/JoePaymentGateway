package com.joe.paymentgateway.exception;

/**
 * Thrown when a cryptographic operation fails.
 *
 * <p>Wraps lower-level exceptions from the Java Cryptography Architecture (JCA)
 * during DUKPT key derivation, ECDH key exchange, or PIN block decryption.</p>
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
