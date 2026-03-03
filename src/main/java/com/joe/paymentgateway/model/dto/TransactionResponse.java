package com.joe.paymentgateway.model.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outgoing response DTO returned after processing a transaction.
 *
 * <p>Contains the transaction result along with ISO 8583 response fields.
 * Sensitive data (full PAN, PIN) is never included in the response.</p>
 *
 * <p>Example JSON response:</p>
 * <pre>
 * {
 *   "transactionId": "a1b2c3d4-...",
 *   "mti": "0210",
 *   "responseCode": "00",
 *   "responseDescription": "Approved",
 *   "status": "APPROVED",
 *   "fields": {
 *     "37": "REF123456789",
 *     "38": "AUTH01",
 *     "39": "00"
 *   },
 *   "timestamp": "2026-02-22T10:30:00Z"
 * }
 * </pre>
 *
 * @param transactionId       unique UUID assigned to this transaction
 * @param mti                 the response MTI (e.g., "0210" for a sale response)
 * @param responseCode        the ISO 8583 Field 39 response code
 * @param responseDescription human-readable description of the response code
 * @param status              the transaction lifecycle status
 * @param fields              ISO 8583 response fields
 * @param timestamp           when the transaction was processed
 */
public record TransactionResponse(
        UUID transactionId,
        String mti,
        String responseCode,
        String responseDescription,
        String status,
        Map<String, String> fields,
        Instant timestamp
) {
}
