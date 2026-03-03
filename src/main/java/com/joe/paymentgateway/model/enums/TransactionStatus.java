package com.joe.paymentgateway.model.enums;

/**
 * Represents the lifecycle status of a payment transaction.
 *
 * <p>Status transitions:</p>
 * <ul>
 *   <li>{@code APPROVED} — Transaction was successfully processed.</li>
 *   <li>{@code DECLINED} — Transaction was rejected (invalid card, insufficient funds, etc.).</li>
 *   <li>{@code VOIDED} — A previously approved transaction has been reversed.</li>
 *   <li>{@code PENDING} — Transaction is awaiting processing (used for pre-auth flows).</li>
 * </ul>
 */
public enum TransactionStatus {

    APPROVED("Approved"),
    DECLINED("Declined"),
    VOIDED("Voided"),
    PENDING("Pending");

    private final String displayName;

    TransactionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
