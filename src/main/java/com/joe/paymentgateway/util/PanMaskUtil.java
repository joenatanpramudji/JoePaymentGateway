package com.joe.paymentgateway.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility class for masking and hashing Primary Account Numbers (PANs).
 *
 * <p>PCI DSS requires that the full PAN is never stored in plain text.
 * This utility provides two operations:</p>
 * <ul>
 *   <li>{@link #mask(String)} — Retains the first 6 and last 4 digits, masks the rest
 *       with asterisks (e.g., "4111111111111111" → "411111******1111").</li>
 *   <li>{@link #hash(String)} — Produces a SHA-256 hash of the PAN for lookup purposes,
 *       so transactions can be queried by card without storing the card number.</li>
 * </ul>
 */
public final class PanMaskUtil {

    private PanMaskUtil() {
        // Prevent instantiation
    }

    /**
     * Masks a PAN by retaining the first 6 and last 4 digits.
     *
     * <p>Example: "4111111111111111" → "411111******1111"</p>
     *
     * @param pan the full Primary Account Number (13–19 digits)
     * @return the masked PAN string
     * @throws IllegalArgumentException if the PAN is null or shorter than 13 digits
     */
    public static String mask(String pan) {
        if (pan == null || pan.length() < 13) {
            throw new IllegalArgumentException("PAN must be at least 13 digits");
        }
        String firstSix = pan.substring(0, 6);
        String lastFour = pan.substring(pan.length() - 4);
        String masked = "*".repeat(pan.length() - 10);
        return firstSix + masked + lastFour;
    }

    /**
     * Produces a SHA-256 hash of the PAN for secure storage and lookup.
     *
     * @param pan the full Primary Account Number
     * @return the lowercase hex-encoded SHA-256 hash (64 characters)
     * @throws IllegalArgumentException if the PAN is null or empty
     */
    public static String hash(String pan) {
        if (pan == null || pan.isEmpty()) {
            throw new IllegalArgumentException("PAN must not be null or empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(pan.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all Java implementations
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
