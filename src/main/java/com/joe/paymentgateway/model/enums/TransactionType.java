package com.joe.paymentgateway.model.enums;

/**
 * Defines the type of payment transaction being processed.
 *
 * <p>Each type maps to a specific ISO 8583 Message Type Indicator (MTI):</p>
 * <ul>
 *   <li>{@code SALE} — MTI 0200: Standard purchase transaction.</li>
 *   <li>{@code VOID} — MTI 0400: Reversal of a previously approved sale.</li>
 *   <li>{@code PRE_AUTH} — MTI 0100: Authorization hold on cardholder funds (Phase 2).</li>
 *   <li>{@code PRE_AUTH_COMPLETION} — MTI 0220: Captures a previously authorized amount (Phase 2).</li>
 * </ul>
 */
public enum TransactionType {

    SALE("0200"),
    VOID("0400"),
    PRE_AUTH("0100"),
    PRE_AUTH_COMPLETION("0220");

    private final String mti;

    TransactionType(String mti) {
        this.mti = mti;
    }

    /**
     * Returns the ISO 8583 Message Type Indicator for this transaction type.
     *
     * @return the 4-digit MTI string
     */
    public String getMti() {
        return mti;
    }

    /**
     * Resolves a {@link TransactionType} from an ISO 8583 MTI string.
     *
     * @param mti the 4-digit MTI (e.g., "0200")
     * @return the matching {@link TransactionType}
     * @throws IllegalArgumentException if the MTI is not recognized
     */
    public static TransactionType fromMti(String mti) {
        for (TransactionType type : values()) {
            if (type.mti.equals(mti)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MTI: " + mti);
    }
}
