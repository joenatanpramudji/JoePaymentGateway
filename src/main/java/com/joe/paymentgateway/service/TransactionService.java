package com.joe.paymentgateway.service;

import com.joe.paymentgateway.exception.InvalidTransactionException;
import com.joe.paymentgateway.exception.TransactionNotFoundException;
import com.joe.paymentgateway.model.dto.Iso8583Fields.FieldNumber;
import com.joe.paymentgateway.model.dto.TransactionRequest;
import com.joe.paymentgateway.model.dto.TransactionResponse;
import com.joe.paymentgateway.model.entity.Transaction;
import com.joe.paymentgateway.model.enums.ResponseCode;
import com.joe.paymentgateway.model.enums.TransactionStatus;
import com.joe.paymentgateway.model.enums.TransactionType;
import com.joe.paymentgateway.repository.TransactionRepository;
import com.joe.paymentgateway.util.IsoFieldValidator;
import com.joe.paymentgateway.util.PanMaskUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core business logic for processing payment transactions.
 *
 * <p>This service orchestrates the complete transaction lifecycle:</p>
 * <ol>
 *   <li>Validates incoming ISO 8583 fields.</li>
 *   <li>Decrypts the PIN block via the crypto service.</li>
 *   <li>Simulates authorization (approve/decline based on validation rules).</li>
 *   <li>Persists the transaction record with masked/hashed PAN.</li>
 *   <li>Records an audit trail entry.</li>
 *   <li>Builds and returns the ISO 8583 response.</li>
 * </ol>
 *
 * <h3>Supported Operations (Phase 1):</h3>
 * <ul>
 *   <li><strong>Sale (MTI 0200)</strong> — Standard purchase transaction.</li>
 *   <li><strong>Void (MTI 0400)</strong> — Reversal of a previously approved sale.</li>
 * </ul>
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final CryptoService cryptoService;
    private final Iso8583Service iso8583Service;
    private final AuditService auditService;

    public TransactionService(TransactionRepository transactionRepository,
                              CryptoService cryptoService,
                              Iso8583Service iso8583Service,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.cryptoService = cryptoService;
        this.iso8583Service = iso8583Service;
        this.auditService = auditService;
    }

    /**
     * Processes a sale transaction (MTI 0200).
     *
     * <p>Workflow:</p>
     * <ol>
     *   <li>Validate all required ISO 8583 fields for a sale.</li>
     *   <li>Check for duplicate transactions (RRN + STAN must be unique).</li>
     *   <li>Decrypt the PIN block using AES-DUKPT.</li>
     *   <li>Simulate PIN validation and card checks.</li>
     *   <li>Persist the transaction with masked PAN and hashed PAN.</li>
     *   <li>Build the ISO 8583 response (MTI 0210).</li>
     *   <li>Log the sanitized audit entry.</li>
     * </ol>
     *
     * @param request the incoming transaction request with ISO 8583 fields
     * @return the transaction response with approval/decline result
     * @throws InvalidTransactionException if field validation fails
     */
    @Transactional
    public TransactionResponse processSale(TransactionRequest request) {
        log.info("Processing sale transaction: MTI={}", request.mti());
        Map<String, String> fields = request.fields();

        // Step 1: Validate required fields
        List<String> errors = IsoFieldValidator.validateSaleFields(fields);
        if (!errors.isEmpty()) {
            throw new InvalidTransactionException("Sale validation failed", errors);
        }

        // Step 2: Check for duplicate transaction (RRN + STAN must be unique)
        String rrn = fields.get(FieldNumber.RRN);
        String stan = fields.get(FieldNumber.STAN);
        if (transactionRepository.existsByRrnAndStan(rrn, stan)) {
            log.warn("Duplicate transaction detected: RRN={}, STAN={}", rrn, stan);
            throw new InvalidTransactionException("Duplicate transaction", List.of(
                    "A transaction with RRN " + rrn + " and STAN " + stan + " already exists"));
        }

        // Step 3: Decrypt PIN block
        String decryptedPinBlock = cryptoService.decryptTransactionPinBlock(
                fields.get(FieldNumber.PIN_BLOCK),
                fields.get(FieldNumber.KSN)
        );
        log.debug("PIN block decrypted successfully");

        // Step 4: Simulate authorization
        ResponseCode responseCode = simulateAuthorization(fields, decryptedPinBlock);

        // Step 5: Build and persist transaction
        Transaction transaction = buildTransaction(fields, TransactionType.SALE, responseCode);
        transactionRepository.save(transaction);
        log.info("Sale transaction saved: id={}, status={}",
                transaction.getTransactionId(), transaction.getStatus());

        // Step 6: Build response
        String responseMti = iso8583Service.buildResponseMti(request.mti());
        String authId = responseCode == ResponseCode.APPROVED ? generateAuthId() : null;
        Map<String, String> responseFields = iso8583Service.buildResponseFields(
                request.mti(), responseCode.getCode(), fields.get(FieldNumber.RRN), authId);

        TransactionResponse response = new TransactionResponse(
                transaction.getTransactionId(),
                responseMti,
                responseCode.getCode(),
                responseCode.getDescription(),
                transaction.getStatus().name(),
                responseFields,
                transaction.getCreatedAt()
        );

        // Step 7: Audit log
        auditService.logTransaction(transaction.getTransactionId(),
                TransactionType.SALE.name(), fields, response);

        return response;
    }

    /**
     * Processes a void transaction (MTI 0400).
     *
     * <p>Reverses a previously approved sale identified by its RRN.
     * The original transaction must exist and have status APPROVED.</p>
     *
     * <p>Workflow:</p>
     * <ol>
     *   <li>Validate required void fields.</li>
     *   <li>Look up the original sale transaction by RRN.</li>
     *   <li>Reject if already voided (explicit "already voided" error).</li>
     *   <li>Reject if not in APPROVED status (only approved transactions can be voided).</li>
     *   <li>Update the original transaction status to VOIDED (no new record created).</li>
     *   <li>Build the void response (MTI 0410).</li>
     *   <li>Log the sanitized audit entry.</li>
     * </ol>
     *
     * @param request the void request containing the original transaction's RRN
     * @return the void transaction response
     * @throws InvalidTransactionException  if field validation fails
     * @throws TransactionNotFoundException if the original sale is not found
     */
    @Transactional
    public TransactionResponse processVoid(TransactionRequest request) {
        log.info("Processing void transaction: MTI={}", request.mti());
        Map<String, String> fields = request.fields();

        // Step 1: Validate required fields
        List<String> errors = IsoFieldValidator.validateVoidFields(fields);
        if (!errors.isEmpty()) {
            throw new InvalidTransactionException("Void validation failed", errors);
        }

        // Step 2: Find original transaction by RRN
        String rrn = fields.get(FieldNumber.RRN);
        Transaction transaction = transactionRepository
                .findByRrnAndTransactionType(rrn, TransactionType.SALE)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "No sale transaction found for RRN: " + rrn));

        // Step 3: Check if already voided
        if (transaction.getStatus() == TransactionStatus.VOIDED) {
            throw new InvalidTransactionException("Void rejected", List.of(
                    "Transaction with RRN " + rrn + " has already been voided"));
        }

        // Step 4: Verify the transaction is in a voidable state (must be APPROVED)
        if (transaction.getStatus() != TransactionStatus.APPROVED) {
            throw new InvalidTransactionException("Void rejected", List.of(
                    "Transaction with RRN " + rrn + " is in " + transaction.getStatus()
                            + " status and cannot be voided"));
        }

        // Step 5: Update the original transaction — void is a status change, not a new record
        transaction.setStatus(TransactionStatus.VOIDED);
        transactionRepository.save(transaction);
        log.info("Transaction voided: id={}, rrn={}", transaction.getTransactionId(), rrn);

        // Step 6: Build response
        String responseMti = iso8583Service.buildResponseMti(request.mti());
        Map<String, String> responseFields = iso8583Service.buildResponseFields(
                request.mti(), ResponseCode.APPROVED.getCode(), rrn, null);

        TransactionResponse response = new TransactionResponse(
                transaction.getTransactionId(),
                responseMti,
                ResponseCode.APPROVED.getCode(),
                ResponseCode.APPROVED.getDescription(),
                TransactionStatus.VOIDED.name(),
                responseFields,
                transaction.getUpdatedAt()
        );

        // Step 7: Audit log
        auditService.logTransaction(transaction.getTransactionId(),
                TransactionType.VOID.name(), fields, response);

        return response;
    }

    /**
     * Retrieves a transaction by its UUID.
     *
     * <p>The response preserves the original authorization response code (e.g., "00")
     * as a historical record, while the {@code status} field reflects the current
     * lifecycle state (e.g., "VOIDED"). The {@code responseDescription} provides
     * context by combining both pieces of information.</p>
     *
     * @param transactionId the UUID of the transaction to look up
     * @return the transaction response
     * @throws TransactionNotFoundException if no matching transaction exists
     */
    public TransactionResponse getTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + transactionId));

        // Build a description that reflects the current state
        String originalDescription = ResponseCode.fromCode(transaction.getResponseCode()).getDescription();
        String description = transaction.getStatus() == TransactionStatus.VOIDED
                ? originalDescription + " (Voided)"
                : originalDescription;

        return new TransactionResponse(
                transaction.getTransactionId(),
                iso8583Service.buildResponseMti(transaction.getMti()),
                transaction.getResponseCode(),
                description,
                transaction.getStatus().name(),
                Map.of(FieldNumber.RRN, transaction.getRrn()),
                transaction.getCreatedAt()
        );
    }

    /**
     * Retrieves a transaction by its Retrieval Reference Number (RRN).
     *
     * @param rrn the RRN from ISO 8583 Field 37
     * @return the transaction response
     * @throws TransactionNotFoundException if no matching transaction exists
     */
    public TransactionResponse getTransactionByRrn(String rrn) {
        Transaction transaction = transactionRepository.findByRrn(rrn)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found for RRN: " + rrn));

        String originalDescription = ResponseCode.fromCode(transaction.getResponseCode()).getDescription();
        String description = transaction.getStatus() == TransactionStatus.VOIDED
                ? originalDescription + " (Voided)"
                : originalDescription;

        return new TransactionResponse(
                transaction.getTransactionId(),
                iso8583Service.buildResponseMti(transaction.getMti()),
                transaction.getResponseCode(),
                description,
                transaction.getStatus().name(),
                Map.of(FieldNumber.RRN, transaction.getRrn()),
                transaction.getCreatedAt()
        );
    }

    /**
     * Simulates authorization logic for a sale transaction.
     *
     * <p>In a real payment gateway, this would connect to the card network (Visa/Mastercard).
     * For this simulation, we apply basic validation rules:</p>
     * <ul>
     *   <li>Check card expiry date (Field 14).</li>
     *   <li>Validate PIN block format.</li>
     *   <li>Simulate approval for valid requests.</li>
     * </ul>
     *
     * @param fields            the ISO 8583 field map
     * @param decryptedPinBlock the decrypted PIN block hex
     * @return the appropriate response code
     */
    private ResponseCode simulateAuthorization(Map<String, String> fields, String decryptedPinBlock) {
        // Check card expiry
        String expiry = fields.get(FieldNumber.EXPIRY_DATE);
        if (expiry != null && isCardExpired(expiry)) {
            log.info("Card expired: {}", expiry);
            return ResponseCode.EXPIRED_CARD;
        }

        // Validate amount is positive
        try {
            long amount = Long.parseLong(fields.get(FieldNumber.AMOUNT));
            if (amount <= 0) {
                return ResponseCode.INVALID_AMOUNT;
            }
        } catch (NumberFormatException e) {
            return ResponseCode.INVALID_AMOUNT;
        }

        // Simulate: approve the transaction
        return ResponseCode.APPROVED;
    }

    /**
     * Checks if a card has expired based on the YYMM expiry format.
     */
    private boolean isCardExpired(String expiryYymm) {
        try {
            int year = Integer.parseInt(expiryYymm.substring(0, 2)) + 2000;
            int month = Integer.parseInt(expiryYymm.substring(2, 4));

            Instant now = Instant.now();
            java.time.YearMonth cardExpiry = java.time.YearMonth.of(year, month);
            java.time.YearMonth currentMonth = java.time.YearMonth.now();

            return cardExpiry.isBefore(currentMonth);
        } catch (Exception e) {
            log.warn("Could not parse expiry date: {}", expiryYymm);
            return false;
        }
    }

    /**
     * Builds a {@link Transaction} entity from ISO 8583 fields and the authorization result.
     */
    private Transaction buildTransaction(Map<String, String> fields, TransactionType type,
                                         ResponseCode responseCode) {
        String pan = fields.get(FieldNumber.PAN);

        Transaction transaction = new Transaction();
        transaction.setMti(type.getMti());
        transaction.setTransactionType(type);
        transaction.setPanMasked(PanMaskUtil.mask(pan));
        transaction.setPanHash(PanMaskUtil.hash(pan));
        transaction.setAmount(Long.parseLong(fields.get(FieldNumber.AMOUNT)));
        transaction.setCurrencyCode(fields.getOrDefault("49", "840")); // Default USD
        transaction.setRrn(fields.get(FieldNumber.RRN));
        transaction.setStan(fields.get(FieldNumber.STAN));
        transaction.setTerminalId(fields.get(FieldNumber.TERMINAL_ID));
        transaction.setMerchantId(fields.get(FieldNumber.MERCHANT_ID));
        transaction.setResponseCode(responseCode.getCode());
        transaction.setStatus(responseCode == ResponseCode.APPROVED
                ? TransactionStatus.APPROVED
                : TransactionStatus.DECLINED);

        return transaction;
    }

    /**
     * Generates a 6-character alphanumeric authorization ID.
     */
    private String generateAuthId() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
