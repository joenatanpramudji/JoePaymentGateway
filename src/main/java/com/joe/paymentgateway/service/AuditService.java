package com.joe.paymentgateway.service;

import com.joe.paymentgateway.model.dto.Iso8583Fields.FieldNumber;
import com.joe.paymentgateway.model.entity.AuditLog;
import com.joe.paymentgateway.repository.AuditLogRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for recording sanitized audit trail entries for all transaction operations.
 *
 * <p>Every transaction attempt (successful or not) is logged with its request and
 * response payloads. Sensitive data is sanitized before storage:</p>
 * <ul>
 *   <li>PAN (Field 2) is replaced with a masked version (e.g., "411111******1111").</li>
 *   <li>PIN Block (Field 52) is completely removed.</li>
 *   <li>KSN (Field 53) is completely removed.</li>
 * </ul>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records an audit entry for a transaction attempt.
     *
     * @param transactionId  the UUID of the transaction
     * @param action         the action performed (e.g., "SALE", "VOID")
     * @param requestFields  the original request fields (will be sanitized before storage)
     * @param responseObject the response object to serialize
     */
    public void logTransaction(UUID transactionId, String action,
                               Map<String, String> requestFields, Object responseObject) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setTransactionId(transactionId);
            auditLog.setAction(action);
            auditLog.setRequestPayload(sanitizeAndSerialize(requestFields));
            auditLog.setResponsePayload(objectMapper.writeValueAsString(responseObject));

            auditLogRepository.save(auditLog);
            log.debug("Audit log recorded for transaction: {} action: {}", transactionId, action);
        } catch (JacksonException e) {
            log.error("Failed to serialize audit payload for transaction: {}", transactionId, e);
        }
    }

    /**
     * Sanitizes request fields by masking/removing sensitive data, then serializes to JSON.
     *
     * @param fields the original ISO 8583 fields
     * @return sanitized JSON string
     */
    private String sanitizeAndSerialize(Map<String, String> fields) throws JacksonException {
        if (fields == null) {
            return "{}";
        }

        Map<String, String> sanitized = new HashMap<>(fields);

        // Mask PAN: keep first 6 and last 4 digits
        String pan = sanitized.get(FieldNumber.PAN);
        if (pan != null && pan.length() >= 13) {
            String masked = pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
            sanitized.put(FieldNumber.PAN, masked);
        }

        // Remove PIN block and KSN entirely
        sanitized.remove(FieldNumber.PIN_BLOCK);
        sanitized.remove(FieldNumber.KSN);

        return objectMapper.writeValueAsString(sanitized);
    }
}
