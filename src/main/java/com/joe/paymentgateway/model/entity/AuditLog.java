package com.joe.paymentgateway.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity that records an immutable audit trail for every transaction attempt.
 *
 * <p>Each entry captures the sanitized request and response payloads for a transaction.
 * Sensitive data (full PAN, PIN) is stripped before logging — only masked PAN
 * and non-sensitive fields are recorded.</p>
 *
 * <p>This table is append-only by design; records should never be updated or deleted.</p>
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * References the {@link Transaction#getTransactionId()} this audit entry belongs to.
     */
    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    /**
     * The action that was performed (e.g., "SALE", "VOID", "KEY_EXCHANGE").
     */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    /**
     * Sanitized JSON representation of the incoming request.
     * PAN is masked, PIN block is removed entirely.
     */
    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    /**
     * JSON representation of the outgoing response.
     */
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Sets the creation timestamp before persisting.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // ===== Getters and Setters =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
