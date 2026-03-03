package com.joe.paymentgateway.controller;

import com.joe.paymentgateway.model.dto.TransactionRequest;
import com.joe.paymentgateway.model.dto.TransactionResponse;
import com.joe.paymentgateway.service.TransactionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for processing ISO 8583 financial transactions.
 *
 * <p>Exposes endpoints for sale and void operations. Each endpoint accepts
 * a JSON request containing ISO 8583 fields and returns a JSON response
 * with the transaction result.</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code POST /api/v1/transactions/sale} — Process a sale (MTI 0200).</li>
 *   <li>{@code POST /api/v1/transactions/void} — Void a previous sale (MTI 0400).</li>
 *   <li>{@code GET /api/v1/transactions/{id}} — Retrieve a transaction by UUID.</li>
 *   <li>{@code GET /api/v1/transactions/rrn/{rrn}} — Retrieve a transaction by RRN.</li>
 * </ul>
 *
 * <h3>Authentication:</h3>
 * <p>All endpoints require HTTP Basic authentication. Credentials are configured
 * per-environment in the application YAML files.</p>
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Processes a sale transaction.
     *
     * <p>Accepts an ISO 8583 message (MTI 0200) wrapped in JSON. The request must
     * contain all required fields including PAN, amount, encrypted PIN block, and KSN.
     * Returns an ISO 8583 response (MTI 0210) with the authorization result.</p>
     *
     * @param request the transaction request containing ISO 8583 fields
     * @return HTTP 200 with the transaction response
     */
    @PostMapping("/sale")
    public ResponseEntity<TransactionResponse> processSale(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received sale request: MTI={}", request.mti());
        TransactionResponse response = transactionService.processSale(request);
        log.info("Sale processed: txnId={}, responseCode={}",
                response.transactionId(), response.responseCode());
        return ResponseEntity.ok(response);
    }

    /**
     * Processes a void transaction.
     *
     * <p>Reverses a previously approved sale identified by its Retrieval Reference Number
     * (RRN) in Field 37. The original sale must exist and be in APPROVED status.</p>
     *
     * @param request the void request containing the original RRN
     * @return HTTP 200 with the void transaction response
     */
    @PostMapping("/void")
    public ResponseEntity<TransactionResponse> processVoid(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received void request: MTI={}", request.mti());
        TransactionResponse response = transactionService.processVoid(request);
        log.info("Void processed: txnId={}, originalRRN={}",
                response.transactionId(), response.fields().get("37"));
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a transaction by its unique identifier.
     *
     * @param id the UUID of the transaction
     * @return HTTP 200 with the transaction details
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID id) {
        log.info("Retrieving transaction: {}", id);
        TransactionResponse response = transactionService.getTransaction(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a transaction by its Retrieval Reference Number (RRN).
     *
     * <p>The RRN (ISO 8583 Field 37) is a 12-character identifier assigned to each
     * transaction. This endpoint allows lookup by RRN as an alternative to UUID,
     * which is useful when the caller only has the RRN from a receipt or terminal.</p>
     *
     * @param rrn the Retrieval Reference Number (Field 37)
     * @return HTTP 200 with the transaction details
     */
    @GetMapping("/rrn/{rrn}")
    public ResponseEntity<TransactionResponse> getTransactionByRrn(@PathVariable String rrn) {
        log.info("Retrieving transaction by RRN: {}", rrn);
        TransactionResponse response = transactionService.getTransactionByRrn(rrn);
        return ResponseEntity.ok(response);
    }
}
