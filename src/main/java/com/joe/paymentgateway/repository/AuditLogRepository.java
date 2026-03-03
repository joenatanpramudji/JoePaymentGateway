package com.joe.paymentgateway.repository;

import com.joe.paymentgateway.model.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditLog} entities.
 *
 * <p>Provides read-only query methods for the audit trail.
 * Audit records are append-only and should never be modified or deleted.</p>
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Retrieves all audit log entries for a specific transaction,
     * ordered by creation time (oldest first).
     *
     * @param transactionId the transaction's UUID
     * @return ordered list of audit entries
     */
    List<AuditLog> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
