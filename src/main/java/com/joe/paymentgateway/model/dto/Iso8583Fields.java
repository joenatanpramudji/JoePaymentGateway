package com.joe.paymentgateway.model.dto;

import java.util.Map;

/**
 * Represents the ISO 8583 field data carried inside a JSON request/response envelope.
 *
 * <p>In production, ISO 8583 messages are binary and sent over TCP. This gateway
 * simulates that by wrapping ISO 8583 fields in a JSON structure for REST API
 * transport. The field numbers follow the ISO 8583:1987 specification.</p>
 *
 * <p>Common fields used in this gateway:</p>
 * <ul>
 *   <li>Field 2  — Primary Account Number (PAN)</li>
 *   <li>Field 3  — Processing Code</li>
 *   <li>Field 4  — Transaction Amount</li>
 *   <li>Field 11 — System Trace Audit Number (STAN)</li>
 *   <li>Field 14 — Card Expiry Date (YYMM)</li>
 *   <li>Field 22 — POS Entry Mode</li>
 *   <li>Field 37 — Retrieval Reference Number (RRN)</li>
 *   <li>Field 41 — Terminal ID</li>
 *   <li>Field 42 — Merchant ID</li>
 *   <li>Field 52 — Encrypted PIN Block</li>
 *   <li>Field 53 — Key Serial Number (KSN) for DUKPT</li>
 * </ul>
 *
 * @param mti    the 4-digit Message Type Indicator (e.g., "0200" for sale)
 * @param fields a map of ISO 8583 field numbers to their string values
 */
public record Iso8583Fields(
        String mti,
        Map<String, String> fields
) {

    /**
     * ISO 8583 field number constants to avoid magic strings throughout the codebase.
     */
    public static final class FieldNumber {
        public static final String PAN = "2";
        public static final String PROCESSING_CODE = "3";
        public static final String AMOUNT = "4";
        public static final String STAN = "11";
        public static final String EXPIRY_DATE = "14";
        public static final String POS_ENTRY_MODE = "22";
        public static final String RRN = "37";
        public static final String RESPONSE_CODE = "39";
        public static final String TERMINAL_ID = "41";
        public static final String MERCHANT_ID = "42";
        public static final String PIN_BLOCK = "52";
        public static final String KSN = "53";

        private FieldNumber() {
            // Prevent instantiation
        }
    }
}
