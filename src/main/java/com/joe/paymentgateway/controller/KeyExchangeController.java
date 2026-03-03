package com.joe.paymentgateway.controller;

import com.joe.paymentgateway.model.dto.KeyExchangeRequest;
import com.joe.paymentgateway.model.dto.KeyExchangeResponse;
import com.joe.paymentgateway.service.CryptoService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the ECDH key exchange protocol.
 *
 * <p>Handles the one-time setup process where a terminal securely receives its
 * Initial PIN Encryption Key (IPEK) from the gateway using Elliptic Curve
 * Diffie-Hellman key agreement.</p>
 *
 * <h3>Protocol Summary:</h3>
 * <ol>
 *   <li>Terminal generates an ECDH key pair and POSTs its public key here.</li>
 *   <li>Gateway generates its own ECDH key pair, derives the shared secret.</li>
 *   <li>Gateway derives the IPEK from BDK + KSN, encrypts it with the shared secret.</li>
 *   <li>Response contains: gateway public key + encrypted IPEK + KSN.</li>
 *   <li>Terminal uses its private key + gateway public key to derive the same shared secret,
 *       then decrypts the IPEK. DUKPT takes over for all future transactions.</li>
 * </ol>
 *
 * <h3>Endpoint:</h3>
 * <ul>
 *   <li>{@code POST /api/v1/key-exchange} — Initiate ECDH key exchange.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/key-exchange")
public class KeyExchangeController {

    private static final Logger log = LoggerFactory.getLogger(KeyExchangeController.class);

    private final CryptoService cryptoService;

    public KeyExchangeController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /**
     * Initiates an ECDH key exchange with a terminal.
     *
     * <p>The terminal sends its ECDH public key. The gateway responds with its own
     * public key and the IPEK encrypted with the derived shared secret.</p>
     *
     * @param request the key exchange request containing the terminal's public key
     * @return HTTP 200 with the gateway's public key, encrypted IPEK, and KSN
     */
    @PostMapping
    public ResponseEntity<KeyExchangeResponse> exchangeKeys(
            @Valid @RequestBody KeyExchangeRequest request) {
        log.info("Key exchange initiated by terminal: {}", request.terminalId());
        KeyExchangeResponse response = cryptoService.processKeyExchange(request);
        log.info("Key exchange completed for terminal: {}", request.terminalId());
        return ResponseEntity.ok(response);
    }
}
