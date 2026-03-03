package com.joe.paymentgateway.model.entity;

import com.joe.paymentgateway.model.enums.TransactionStatus;
import com.joe.paymentgateway.model.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a financial transaction record.
 *
 * <p>Stores the result of processing an ISO 8583 message (sale, void, pre-auth).
 * Sensitive data handling:</p>
 * <ul>
 *   <li>PAN (card number) is stored as a masked value (e.g., "411111****1111")
 *       and a SHA-256 hash for lookup purposes.</li>
 *   <li>PIN is NEVER stored — it is decrypted in memory for validation only.</li>
 * </ul>
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for this transaction, used in API responses and cross-references.
     */
    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private UUID transactionId;

    /**
     * ISO 8583 Message Type Indicator (e.g., "0200" for sale, "0400" for void).
     */
    @Column(name = "mti", nullable = false, length = 4)
    private String mti;

    /**
     * The type of transaction (SALE, VOID, PRE_AUTH, PRE_AUTH_COMPLETION).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 25)
    private TransactionType transactionType;

    /**
     * Masked PAN for display purposes (e.g., "411111****1111").
     * The full PAN is never persisted.
     */
    @Column(name = "pan_masked", nullable = false, length = 19)
    private String panMasked;

    /**
     * SHA-256 hash of the full PAN, used for looking up transactions by card number
     * without storing the actual card number.
     */
    @Column(name = "pan_hash", nullable = false, length = 64)
    private String panHash;

    /**
     * Transaction amount in the smallest currency unit (e.g., cents for USD).
     * A $50.00 transaction is stored as 5000.
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * ISO 4217 numeric currency code (e.g., "840" for USD).
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /**
     * Retrieval Reference Number — unique reference assigned by the acquirer,
     * used to identify and retrieve the original transaction.
     */
    @Column(name = "rrn", nullable = false, length = 12)
    private String rrn;

    /**
     * System Trace Audit Number — a unique 6-digit number assigned by the terminal
     * to identify each transaction within a single business day.
     */
    @Column(name = "stan", nullable = false, length = 6)
    private String stan;

    /**
     * Identifier of the terminal (POS device) that initiated the transaction.
     */
    @Column(name = "terminal_id", nullable = false, length = 8)
    private String terminalId;

    /**
     * Identifier of the merchant where the transaction took place.
     */
    @Column(name = "merchant_id", nullable = false, length = 15)
    private String merchantId;

    /**
     * ISO 8583 Field 39 response code (e.g., "00" for approved, "05" for declined).
     */
    @Column(name = "response_code", nullable = false, length = 2)
    private String responseCode;

    /**
     * Current lifecycle status of this transaction.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private TransactionStatus status;

    /**
     * For VOID transactions, this references the original sale's transactionId.
     * Null for non-void transactions.
     */
    @Column(name = "original_transaction_id")
    private UUID originalTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Automatically sets timestamps and generates a transaction UUID before persisting.
     */
    @PrePersist
    protected void onCreate() {
        if (transactionId == null) {
            transactionId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    /**
     * Updates the modification timestamp before each update.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
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

    public String getMti() {
        return mti;
    }

    public void setMti(String mti) {
        this.mti = mti;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public String getPanMasked() {
        return panMasked;
    }

    public void setPanMasked(String panMasked) {
        this.panMasked = panMasked;
    }

    public String getPanHash() {
        return panHash;
    }

    public void setPanHash(String panHash) {
        this.panHash = panHash;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getRrn() {
        return rrn;
    }

    public void setRrn(String rrn) {
        this.rrn = rrn;
    }

    public String getStan() {
        return stan;
    }

    public void setStan(String stan) {
        this.stan = stan;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public UUID getOriginalTransactionId() {
        return originalTransactionId;
    }

    public void setOriginalTransactionId(UUID originalTransactionId) {
        this.originalTransactionId = originalTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
