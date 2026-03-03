package com.joe.paymentgateway.repository;

import com.joe.paymentgateway.model.entity.Transaction;
import com.joe.paymentgateway.model.enums.TransactionStatus;
import com.joe.paymentgateway.model.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Transaction} entities.
 *
 * <p>Provides CRUD operations and custom query methods for looking up
 * transactions by their business identifiers (UUID, RRN, PAN hash).</p>
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Finds a transaction by its unique business identifier.
     *
     * @param transactionId the UUID assigned at transaction creation
     * @return the transaction if found
     */
    Optional<Transaction> findByTransactionId(UUID transactionId);

    /**
     * Finds the original sale transaction by its RRN.
     * Filters by transaction type to avoid collisions with void records
     * that share the same RRN.
     *
     * @param rrn             the 12-character RRN from ISO 8583 Field 37
     * @param transactionType the transaction type to filter by (e.g., SALE)
     * @return the matching transaction if found
     */
    Optional<Transaction> findByRrnAndTransactionType(String rrn, TransactionType transactionType);

    /**
     * Finds all transactions for a specific card (identified by PAN hash)
     * with a given status.
     *
     * @param panHash the SHA-256 hash of the PAN
     * @param status  the transaction status to filter by
     * @return list of matching transactions
     */
    List<Transaction> findByPanHashAndStatus(String panHash, TransactionStatus status);

    /**
     * Finds a transaction by its RRN and status. Used primarily to locate
     * the original approved sale when processing a void request.
     *
     * @param rrn    the Retrieval Reference Number
     * @param status the expected status (e.g., APPROVED)
     * @return the matching transaction if found
     */
    Optional<Transaction> findByRrnAndStatus(String rrn, TransactionStatus status);

    /**
     * Checks if a transaction already exists with the given RRN and STAN combination.
     * Used for duplicate detection — in real payment systems, the RRN + STAN pair
     * uniquely identifies a transaction within a given business day.
     *
     * @param rrn  the Retrieval Reference Number (Field 37)
     * @param stan the System Trace Audit Number (Field 11)
     * @return true if a matching transaction already exists
     */
    boolean existsByRrnAndStan(String rrn, String stan);

    /**
     * Finds a transaction by its Retrieval Reference Number.
     * Used for RRN-based lookups from the GET endpoint.
     *
     * @param rrn the Retrieval Reference Number (Field 37)
     * @return the matching transaction if found
     */
    Optional<Transaction> findByRrn(String rrn);
}
