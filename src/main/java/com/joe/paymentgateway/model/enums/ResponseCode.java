package com.joe.paymentgateway.model.enums;

/**
 * ISO 8583 response codes returned in Field 39 of the authorization response.
 *
 * <p>These codes indicate the result of a transaction request. Only the most
 * common codes used in this gateway simulation are included.</p>
 *
 * @see <a href="https://en.wikipedia.org/wiki/ISO_8583#Response_code">ISO 8583 Response Codes</a>
 */
public enum ResponseCode {

    APPROVED("00", "Approved"),
    DECLINED("05", "Do Not Honor"),
    INVALID_TRANSACTION("12", "Invalid Transaction"),
    INVALID_AMOUNT("13", "Invalid Amount"),
    INVALID_CARD_NUMBER("14", "Invalid Card Number"),
    EXPIRED_CARD("54", "Expired Card"),
    INSUFFICIENT_FUNDS("51", "Insufficient Funds"),
    PIN_INCORRECT("55", "Incorrect PIN"),
    TRANSACTION_NOT_FOUND("25", "Unable to Locate Record"),
    DUPLICATE_TRANSACTION("94", "Duplicate Transaction"),
    SYSTEM_ERROR("96", "System Malfunction");

    private final String code;
    private final String description;

    ResponseCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns the 2-digit ISO 8583 response code.
     *
     * @return the response code string (e.g., "00", "05")
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the human-readable description of this response code.
     *
     * @return the description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Resolves a {@link ResponseCode} from a 2-digit code string.
     *
     * @param code the 2-digit response code (e.g., "00")
     * @return the matching {@link ResponseCode}
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static ResponseCode fromCode(String code) {
        for (ResponseCode rc : values()) {
            if (rc.code.equals(code)) {
                return rc;
            }
        }
        throw new IllegalArgumentException("Unknown response code: " + code);
    }
}
