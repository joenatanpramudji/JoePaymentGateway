package com.joe.paymentgateway.model.dto;

/**
 * Response DTO returned after a successful ECDH key exchange.
 *
 * <p>Contains the gateway's ECDH public key and the Initial PIN Encryption Key (IPEK)
 * encrypted with the shared secret derived from the ECDH exchange. The terminal
 * can then decrypt the IPEK using its own private key + the gateway's public key
 * to derive the same shared secret.</p>
 *
 * @param gatewayPublicKeyBase64  the gateway's ECDH public key, Base64-encoded
 * @param encryptedIpekBase64     the IPEK encrypted with the ECDH shared secret, Base64-encoded
 * @param ksn                     the initial Key Serial Number assigned to this terminal
 */
public record KeyExchangeResponse(
        String gatewayPublicKeyBase64,
        String encryptedIpekBase64,
        String ksn
) {
}
