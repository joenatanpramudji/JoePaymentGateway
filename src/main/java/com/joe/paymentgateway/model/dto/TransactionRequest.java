package com.joe.paymentgateway.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Incoming request DTO for all transaction types (sale, void, pre-auth).
 *
 * <p>The client sends a JSON body containing ISO 8583 fields. This DTO
 * captures the top-level structure before the fields are parsed and
 * validated by the ISO 8583 service layer.</p>
 *
 * <p>Example JSON request body:</p>
 * <pre>
 * {
 *   "mti": "0200",
 *   "fields": {
 *     "2":  "4111111111111111",
 *     "3":  "000000",
 *     "4":  "000000005000",
 *     "11": "123456",
 *     "14": "2612",
 *     "37": "REF123456789",
 *     "41": "TERM0001",
 *     "42": "MERCHANT000001",
 *     "52": "ENCRYPTED_PIN_BLOCK_HEX",
 *     "53": "KSN_HEX_VALUE"
 *   }
 * }
 * </pre>
 *
 * @param mti    the ISO 8583 Message Type Indicator (e.g., "0200")
 * @param fields map of ISO 8583 field numbers to their string values
 */
public record TransactionRequest(

        @NotBlank(message = "MTI is required")
        @Size(min = 4, max = 4, message = "MTI must be exactly 4 digits")
        String mti,

        @NotNull(message = "ISO 8583 fields are required")
        Map<String, String> fields

) {
}
