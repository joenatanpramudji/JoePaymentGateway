package com.joe.paymentgateway.service;

import com.joe.paymentgateway.config.CryptoConfig;
import com.joe.paymentgateway.exception.CryptoException;
import com.joe.paymentgateway.model.dto.KeyExchangeRequest;
import com.joe.paymentgateway.model.dto.KeyExchangeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HexFormat;

/**
 * Orchestrates all cryptographic operations for the payment gateway.
 *
 * <p>This service is the single entry point for crypto operations used by the
 * controller and transaction service layers. It delegates to specialized services:</p>
 * <ul>
 *   <li>{@link DukptService} — AES-256 DUKPT key derivation and PIN block encryption.</li>
 *   <li>{@link EcdhKeyExchangeService} — ECDH key exchange for secure IPEK delivery.</li>
 * </ul>
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Processing ECDH key exchange requests to deliver IPEKs to terminals.</li>
 *   <li>Deriving DUKPT session keys from the BDK and incoming KSN values.</li>
 *   <li>Decrypting PIN blocks received in transaction requests.</li>
 * </ul>
 */
@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    private final DukptService dukptService;
    private final EcdhKeyExchangeService ecdhService;
    private final CryptoConfig cryptoConfig;

    public CryptoService(DukptService dukptService,
                         EcdhKeyExchangeService ecdhService,
                         CryptoConfig cryptoConfig) {
        this.dukptService = dukptService;
        this.ecdhService = ecdhService;
        this.cryptoConfig = cryptoConfig;
    }

    /**
     * Processes an ECDH key exchange request from a terminal.
     *
     * <p>Steps performed:</p>
     * <ol>
     *   <li>Generate a gateway ECDH key pair.</li>
     *   <li>Decode the terminal's public key from the request.</li>
     *   <li>Derive the shared secret using ECDH.</li>
     *   <li>Generate a KSN for this terminal.</li>
     *   <li>Derive the IPEK from the BDK and the new KSN.</li>
     *   <li>Encrypt the IPEK with the shared secret (AES-GCM).</li>
     *   <li>Return the gateway's public key, encrypted IPEK, and KSN.</li>
     * </ol>
     *
     * @param request the key exchange request containing the terminal's public key
     * @return response containing the gateway's public key, encrypted IPEK, and KSN
     * @throws CryptoException if any crypto operation fails
     */
    public KeyExchangeResponse processKeyExchange(KeyExchangeRequest request) {
        log.info("Processing key exchange for terminal: {}", request.terminalId());

        // Step 1: Generate gateway ECDH key pair
        KeyPair gatewayKeyPair = ecdhService.generateKeyPair();
        String gatewayPublicKeyBase64 = ecdhService.encodePublicKey(gatewayKeyPair);

        // Step 2: Decode terminal's public key
        PublicKey terminalPublicKey = ecdhService.decodePublicKey(request.publicKeyBase64());

        // Step 3: Derive shared secret
        byte[] sharedSecret = ecdhService.deriveSharedSecret(gatewayKeyPair, terminalPublicKey);

        // Step 4: Generate KSN for this terminal (Key Set ID + Terminal ID + Counter 000000)
        String ksn = generateKsn(request.terminalId());

        // Step 5: Derive IPEK from BDK and KSN
        String ipekHex = dukptService.deriveIpek(cryptoConfig.getBdk(), ksn);

        // Step 6: Encrypt IPEK with shared secret
        String encryptedIpekBase64 = ecdhService.encryptIpekWithSharedSecret(ipekHex, sharedSecret);

        log.info("Key exchange completed for terminal: {}", request.terminalId());

        // Step 7: Return response (gateway public key + encrypted IPEK + KSN)
        return new KeyExchangeResponse(gatewayPublicKeyBase64, encryptedIpekBase64, ksn);
    }

    /**
     * Decrypts a PIN block from an incoming transaction request.
     *
     * <p>Derives the session key from the BDK and KSN using the DUKPT key hierarchy,
     * then uses that session key to decrypt the PIN block.</p>
     *
     * @param encryptedPinBlockHex the encrypted PIN block from ISO 8583 Field 52
     * @param ksnHex               the Key Serial Number from ISO 8583 Field 53
     * @return the decrypted PIN block as a hex string
     * @throws CryptoException if decryption fails
     */
    public String decryptTransactionPinBlock(String encryptedPinBlockHex, String ksnHex) {
        log.debug("Decrypting PIN block for KSN: {}...{}",
                ksnHex.substring(0, 8), ksnHex.substring(ksnHex.length() - 6));

        // Derive IPEK from BDK + KSN
        String ipekHex = dukptService.deriveIpek(cryptoConfig.getBdk(), ksnHex);

        // Derive session key from IPEK + KSN (with counter)
        String sessionKeyHex = dukptService.deriveSessionKey(ipekHex, ksnHex);

        // Decrypt the PIN block
        return dukptService.decryptPinBlock(encryptedPinBlockHex, sessionKeyHex);
    }

    /**
     * Generates a 24-character hex KSN for a terminal.
     *
     * <p>Format: [8-char Key Set ID][8-char Terminal ID padded][8-char zero counter]</p>
     *
     * @param terminalId the terminal identifier (up to 8 characters)
     * @return the 24-character hex KSN string
     */
    private String generateKsn(String terminalId) {
        // Key Set ID: first 8 hex chars from a hash of the BDK (identifies which BDK)
        String keySetId = cryptoConfig.getBdk().substring(0, 8);

        // Terminal ID: pad/truncate to 8 hex chars
        String terminalIdHex = HexFormat.of().formatHex(
                terminalId.getBytes()).substring(0, Math.min(16, terminalId.length() * 2));
        terminalIdHex = String.format("%-16s", terminalIdHex).replace(' ', '0').substring(0, 8);

        // Counter starts at 000000 (6 hex chars = 3 bytes)
        String counter = "00000000";

        return keySetId + terminalIdHex + counter;
    }
}
