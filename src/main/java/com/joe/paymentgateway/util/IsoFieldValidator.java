package com.joe.paymentgateway.util;

import com.joe.paymentgateway.model.dto.Iso8583Fields.FieldNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates ISO 8583 fields from incoming transaction requests.
 *
 * <p>Checks for the presence and basic format of required fields based on
 * the transaction type (MTI). Returns a list of human-readable validation
 * error messages; an empty list indicates all validations passed.</p>
 */
public final class IsoFieldValidator {

    private IsoFieldValidator() {
        // Prevent instantiation
    }

    /**
     * Validates the fields required for a sale transaction (MTI 0200).
     *
     * <p>Required fields: PAN (2), Processing Code (3), Amount (4), STAN (11),
     * Expiry Date (14), RRN (37), Terminal ID (41), Merchant ID (42),
     * PIN Block (52), KSN (53).</p>
     *
     * @param fields the ISO 8583 fields map from the request
     * @return a list of validation error messages (empty if valid)
     */
    public static List<String> validateSaleFields(Map<String, String> fields) {
        List<String> errors = new ArrayList<>();

        requireField(fields, FieldNumber.PAN, "Primary Account Number (Field 2)", errors);
        requireField(fields, FieldNumber.PROCESSING_CODE, "Processing Code (Field 3)", errors);
        requireField(fields, FieldNumber.AMOUNT, "Transaction Amount (Field 4)", errors);
        requireField(fields, FieldNumber.STAN, "STAN (Field 11)", errors);
        requireField(fields, FieldNumber.EXPIRY_DATE, "Expiry Date (Field 14)", errors);
        requireField(fields, FieldNumber.RRN, "RRN (Field 37)", errors);
        requireField(fields, FieldNumber.TERMINAL_ID, "Terminal ID (Field 41)", errors);
        requireField(fields, FieldNumber.MERCHANT_ID, "Merchant ID (Field 42)", errors);
        requireField(fields, FieldNumber.PIN_BLOCK, "PIN Block (Field 52)", errors);
        requireField(fields, FieldNumber.KSN, "KSN (Field 53)", errors);

        // Format validations
        validatePan(fields.get(FieldNumber.PAN), errors);
        validateAmount(fields.get(FieldNumber.AMOUNT), errors);
        validateStan(fields.get(FieldNumber.STAN), errors);
        validateExpiryDate(fields.get(FieldNumber.EXPIRY_DATE), errors);

        return errors;
    }

    /**
     * Validates the fields required for a void transaction (MTI 0400).
     *
     * <p>Required fields: RRN (37) of the original transaction, Terminal ID (41),
     * Merchant ID (42). The original transaction is looked up by RRN.</p>
     *
     * @param fields the ISO 8583 fields map from the request
     * @return a list of validation error messages (empty if valid)
     */
    public static List<String> validateVoidFields(Map<String, String> fields) {
        List<String> errors = new ArrayList<>();

        requireField(fields, FieldNumber.RRN, "RRN (Field 37)", errors);
        requireField(fields, FieldNumber.TERMINAL_ID, "Terminal ID (Field 41)", errors);
        requireField(fields, FieldNumber.MERCHANT_ID, "Merchant ID (Field 42)", errors);

        return errors;
    }

    /**
     * Checks that a required field is present and non-blank.
     */
    private static void requireField(Map<String, String> fields, String fieldNumber,
                                     String fieldName, List<String> errors) {
        String value = fields.get(fieldNumber);
        if (value == null || value.isBlank()) {
            errors.add("Missing required field: " + fieldName);
        }
    }

    /**
     * Validates PAN format: must be 13–19 digits, all numeric.
     */
    private static void validatePan(String pan, List<String> errors) {
        if (pan == null) return; // Already caught by requireField
        if (!pan.matches("\\d{13,19}")) {
            errors.add("PAN (Field 2) must be 13-19 digits");
        }
    }

    /**
     * Validates amount: must be a 12-digit zero-padded numeric string.
     */
    private static void validateAmount(String amount, List<String> errors) {
        if (amount == null) return;
        if (!amount.matches("\\d{1,12}")) {
            errors.add("Amount (Field 4) must be numeric, up to 12 digits");
        }
        try {
            long value = Long.parseLong(amount);
            if (value <= 0) {
                errors.add("Amount (Field 4) must be greater than zero");
            }
        } catch (NumberFormatException e) {
            errors.add("Amount (Field 4) is not a valid number");
        }
    }

    /**
     * Validates STAN: must be exactly 6 digits.
     */
    private static void validateStan(String stan, List<String> errors) {
        if (stan == null) return;
        if (!stan.matches("\\d{6}")) {
            errors.add("STAN (Field 11) must be exactly 6 digits");
        }
    }

    /**
     * Validates expiry date: must be 4 digits in YYMM format with a valid month.
     */
    private static void validateExpiryDate(String expiry, List<String> errors) {
        if (expiry == null) return;
        if (!expiry.matches("\\d{4}")) {
            errors.add("Expiry Date (Field 14) must be 4 digits (YYMM)");
            return;
        }
        int month = Integer.parseInt(expiry.substring(2));
        if (month < 1 || month > 12) {
            errors.add("Expiry Date (Field 14) has invalid month");
        }
    }
}
