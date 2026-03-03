package com.joe.paymentgateway.service;

import com.joe.paymentgateway.model.dto.Iso8583Fields;
import com.joe.paymentgateway.model.dto.Iso8583Fields.FieldNumber;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for parsing and building ISO 8583 messages using the jPOS library.
 *
 * <p>This service bridges the JSON-based REST API with the ISO 8583 message standard.
 * Incoming JSON requests contain ISO 8583 fields as a map; this service converts them
 * to jPOS {@link ISOMsg} objects for validation and processing, and converts
 * response messages back to field maps for JSON serialization.</p>
 *
 * <h3>Why jPOS:</h3>
 * <p>jPOS is the industry-standard Java library for ISO 8583 message handling. It provides
 * proper field packing/unpacking, validation, and supports all ISO 8583 variants.</p>
 *
 * <h3>MTI Response Mapping:</h3>
 * <ul>
 *   <li>0200 (Sale request) → 0210 (Sale response)</li>
 *   <li>0400 (Void request) → 0410 (Void response)</li>
 *   <li>0100 (Pre-auth request) → 0110 (Pre-auth response)</li>
 * </ul>
 */
@Service
public class Iso8583Service {

    private static final Logger log = LoggerFactory.getLogger(Iso8583Service.class);

    /**
     * Converts a JSON field map into a jPOS {@link ISOMsg} for internal processing.
     *
     * <p>Sets the MTI and populates each ISO 8583 data element from the map.
     * Field numbers are string keys (e.g., "2" for PAN, "4" for amount).</p>
     *
     * @param iso8583Fields the parsed request containing MTI and field map
     * @return a populated jPOS ISOMsg
     * @throws ISOException if a field cannot be set (invalid field number or format)
     */
    public ISOMsg parseRequest(Iso8583Fields iso8583Fields) throws ISOException {
        ISOMsg isoMsg = new ISOMsg();
        isoMsg.setMTI(iso8583Fields.mti());

        for (Map.Entry<String, String> entry : iso8583Fields.fields().entrySet()) {
            int fieldNumber = Integer.parseInt(entry.getKey());
            isoMsg.set(fieldNumber, entry.getValue());
        }

        log.debug("Parsed ISO 8583 message: MTI={}, fields={}", iso8583Fields.mti(),
                iso8583Fields.fields().size());
        return isoMsg;
    }

    /**
     * Builds an ISO 8583 response message from a processed transaction.
     *
     * <p>Creates the response MTI by incrementing the second digit of the request MTI
     * (e.g., 0200 → 0210), and populates response-specific fields like the
     * response code (Field 39) and authorization ID (Field 38).</p>
     *
     * @param requestMti   the original request MTI (e.g., "0200")
     * @param responseCode the ISO 8583 response code for Field 39 (e.g., "00")
     * @param rrn          the Retrieval Reference Number to echo back
     * @param authId       the authorization identification response (Field 38)
     * @return a map of ISO 8583 response fields
     */
    public Map<String, String> buildResponseFields(String requestMti, String responseCode,
                                                   String rrn, String authId) {
        Map<String, String> responseFields = new HashMap<>();

        responseFields.put(FieldNumber.RRN, rrn);
        responseFields.put(FieldNumber.RESPONSE_CODE, responseCode);

        // Field 38: Authorization ID Response — only present for approved transactions
        if ("00".equals(responseCode) && authId != null) {
            responseFields.put("38", authId);
        }

        log.debug("Built ISO 8583 response: MTI={}, responseCode={}",
                buildResponseMti(requestMti), responseCode);
        return responseFields;
    }

    /**
     * Derives the response MTI from the request MTI.
     *
     * <p>Per ISO 8583 convention, the response MTI increments the third digit by 1:</p>
     * <ul>
     *   <li>0200 → 0210 (Financial transaction request → response)</li>
     *   <li>0400 → 0410 (Reversal request → response)</li>
     *   <li>0100 → 0110 (Authorization request → response)</li>
     * </ul>
     *
     * @param requestMti the 4-digit request MTI
     * @return the 4-digit response MTI
     */
    public String buildResponseMti(String requestMti) {
        // Response MTI: change position 2 (0-indexed) from '0' to '1'
        // e.g., "0200" → "0210"
        char[] mtiChars = requestMti.toCharArray();
        mtiChars[2] = (char) (mtiChars[2] + 1);
        return new String(mtiChars);
    }

    /**
     * Extracts a specific field value from a jPOS message.
     *
     * @param isoMsg      the jPOS message
     * @param fieldNumber the ISO 8583 field number to extract
     * @return the field value, or null if not present
     */
    public String getField(ISOMsg isoMsg, int fieldNumber) {
        return isoMsg.hasField(fieldNumber) ? isoMsg.getString(fieldNumber) : null;
    }
}
