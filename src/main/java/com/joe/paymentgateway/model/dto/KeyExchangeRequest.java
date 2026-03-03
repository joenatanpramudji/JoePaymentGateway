package com.joe.paymentgateway.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for the ECDH key exchange initiation.
 *
 * <p>The terminal sends its ECDH public key to the gateway. The gateway
 * responds with its own public key and the IPEK encrypted with the
 * derived shared secret.</p>
 *
 * @param terminalId       the identifier of the requesting terminal
 * @param publicKeyBase64  the terminal's ECDH public key, Base64-encoded
 */
public record KeyExchangeRequest(

        @NotBlank(message = "Terminal ID is required")
        String terminalId,

        @NotBlank(message = "Public key is required")
        String publicKeyBase64

) {
}
