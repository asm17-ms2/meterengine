package com.meterengine.payment.repository;

import com.meterengine.payment.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

  @Query(
      "select a from PaymentAttempt a where a.organizationId = :organizationId and a.environmentId"
          + " IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()} and"
          + " a.invoiceId = :invoiceId and a.mode = :mode and a.status in ('pending', 'done')")
  Optional<PaymentAttempt> findActive(UUID organizationId, UUID invoiceId, String mode);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update PaymentAttempt a set a.status = 'done', a.paymentKey = :paymentKey,"
          + " a.completedAt = :completedAt where a.id = :id and a.status = 'pending'")
  int markDone(UUID id, String paymentKey, OffsetDateTime completedAt);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update PaymentAttempt a set a.status = 'failed', a.failureCode = :failureCode,"
          + " a.failureMessage = :failureMessage, a.completedAt = :completedAt"
          + " where a.id = :id and a.status = 'pending'")
  int markFailed(UUID id, String failureCode, String failureMessage, OffsetDateTime completedAt);

  @Query(
      "select a from PaymentAttempt a where a.organizationId = :organizationId and a.id = :id and"
          + " a.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()}")
  Optional<PaymentAttempt> findByOrganizationIdAndId(UUID organizationId, UUID id);

  @Query("select a from PaymentAttempt a where a.organizationId = :organizationId and a.id = :id")
  Optional<PaymentAttempt> findUnscoped(UUID organizationId, UUID id);

  @Query(
      "select a from PaymentAttempt a where a.organizationId = :organizationId and a.invoiceId ="
          + " :invoiceId and a.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()}"
          + " order by a.requestedAt desc, a.id desc")
  List<PaymentAttempt> findByOrganizationIdAndInvoiceIdOrderByRequestedAtDesc(
      UUID organizationId, UUID invoiceId);

  @Query(
      "select count(a) from PaymentAttempt a where a.organizationId = :organizationId and"
          + " a.invoiceId = :invoiceId and a.mode = :mode and a.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()}")
  long countByOrganizationIdAndInvoiceIdAndMode(UUID organizationId, UUID invoiceId, String mode);

  boolean existsByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);

  @Query(
      nativeQuery = true,
      value =
          """
          SELECT i.id AS "invoiceId",
                 i.total_amount AS "amount",
                 latest.status AS "latestStatus",
                 latest.failure_code AS "latestFailureCode",
                 latest.completed_at AS "latestCompletedAt",
                 attempts.attempt_count AS "attemptCount",
                 cancellations.canceled_amount AS "canceledAmount",
                 bk.authenticated_at AS "billingKeyAuthenticatedAt"
          FROM invoice i
          LEFT JOIN billing_key bk
            ON bk.organization_id = i.organization_id
           AND bk.customer_id = i.customer_id
           AND bk.mode = :mode
           AND bk.environment_id IS NOT DISTINCT FROM i.environment_id
           AND bk.archived_at IS NULL
          LEFT JOIN LATERAL (
            SELECT a.id, a.status, a.failure_code, a.completed_at
            FROM payment_attempt a
            WHERE a.organization_id = i.organization_id
              AND a.invoice_id = i.id
              AND a.environment_id IS NOT DISTINCT FROM i.environment_id
              AND a.mode = :mode
            ORDER BY a.requested_at DESC, a.id DESC
            LIMIT 1
          ) latest ON true
          LEFT JOIN LATERAL (
            SELECT count(*) AS attempt_count
            FROM payment_attempt a
            WHERE a.organization_id = i.organization_id
              AND a.invoice_id = i.id
              AND a.environment_id IS NOT DISTINCT FROM i.environment_id
              AND a.mode = :mode
          ) attempts ON true
          LEFT JOIN LATERAL (
            SELECT CAST(coalesce(sum(c.amount), 0) AS BIGINT) AS canceled_amount
            FROM payment_cancellation c
            WHERE c.organization_id = i.organization_id
              AND c.payment_attempt_id = latest.id
              AND latest.status = 'done'
              AND c.status = 'done'
          ) cancellations ON true
          WHERE i.organization_id = :organizationId
            AND i.environment_id IS NOT DISTINCT FROM CAST(:#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()} AS UUID)
            AND (CAST(:customerId AS UUID) IS NULL OR i.customer_id = CAST(:customerId AS UUID))
            AND (CAST(:period AS VARCHAR) IS NULL OR i.period = CAST(:period AS VARCHAR))
          ORDER BY i.period DESC, i.customer_name ASC, i.customer_id ASC
          """)
  List<InvoicePaymentProjection> findInvoicePayments(
      UUID organizationId, UUID customerId, String period, String mode);

  @Query(
      "select a from PaymentAttempt a where a.status = 'pending'"
          + " and a.requestedAt >= :requestedFrom and a.requestedAt < :requestedBefore"
          + " order by a.requestedAt asc, a.id asc")
  List<PaymentAttempt> findPendingRequestedBetween(
      OffsetDateTime requestedFrom, OffsetDateTime requestedBefore);

  @Query(
      "select count(a) from PaymentAttempt a where a.status = 'pending' and a.mode = :mode"
          + " and a.requestedAt < :requestedBefore")
  long countPendingRequestedBefore(String mode, OffsetDateTime requestedBefore);

  @Query(
      "select min(a.requestedAt) from PaymentAttempt a where a.status = 'pending'"
          + " and a.mode = :mode and a.requestedAt < :requestedBefore")
  Optional<OffsetDateTime> findOldestPendingRequestedAt(
      String mode, OffsetDateTime requestedBefore);

  @Query(
      nativeQuery = true,
      value =
          """
          SELECT *
          FROM payment_attempt
          WHERE organization_id = :organizationId
            AND invoice_id = :invoiceId
            AND payment_attempt.environment_id IS NOT DISTINCT FROM CAST(:#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()} AS UUID)
            AND mode = :mode
          ORDER BY requested_at DESC, id DESC
          LIMIT 1
          """)
  Optional<PaymentAttempt> findLatest(UUID organizationId, UUID invoiceId, String mode);

  @Query(
      nativeQuery = true,
      value =
          """
          SELECT a.id AS "id",
                 a.organization_id AS "organizationId",
                 a.environment_id AS "environmentId",
                 a.invoice_id AS "invoiceId",
                 a.mode AS "mode",
                 a.failure_code AS "failureCode",
                 a.requested_at AS "requestedAt",
                 a.completed_at AS "completedAt",
                 attempts.attempt_count AS "attemptCount",
                 bk.authenticated_at AS "billingKeyAuthenticatedAt"
          FROM payment_attempt a
          JOIN organization o ON o.id = a.organization_id
          LEFT JOIN payment_setting s ON s.organization_id = a.organization_id
          JOIN billing_key bk
            ON bk.organization_id = a.organization_id
           AND bk.customer_id = a.customer_id
           AND bk.mode = a.mode
           AND bk.environment_id IS NOT DISTINCT FROM a.environment_id
           AND bk.archived_at IS NULL
          CROSS JOIN LATERAL (
            SELECT count(*) AS attempt_count
            FROM payment_attempt c
            WHERE c.organization_id = a.organization_id
              AND c.invoice_id = a.invoice_id
              AND c.mode = a.mode
              AND c.environment_id IS NOT DISTINCT FROM a.environment_id
          ) attempts
          WHERE a.status = 'failed'
            AND o.status = 'active'
            AND (a.environment_id IS NOT NULL OR a.mode = coalesce(s.mode, 'test'))
            AND a.completed_at <= :completedBefore
            AND attempts.attempt_count < :maxAttempts
            AND (a.failure_code NOT IN (:nonRetryableFailureCodes)
                 OR bk.authenticated_at > a.completed_at
                 OR (a.failure_code = :removedBillingKeyFailureCode
                     AND bk.authenticated_at IS NOT NULL))
            AND (a.failure_code <> :notFoundFailureCode
                 OR a.completed_at >= :notFoundRetryableFrom)
            AND NOT EXISTS (
              SELECT 1
              FROM payment_attempt newer
              WHERE newer.organization_id = a.organization_id
                AND newer.invoice_id = a.invoice_id
                AND newer.mode = a.mode
                AND newer.environment_id IS NOT DISTINCT FROM a.environment_id
                AND (newer.requested_at, newer.id) > (a.requested_at, a.id)
            )
          ORDER BY a.completed_at ASC, a.id ASC
          """)
  List<PaymentRetryCandidateProjection> findRetryCandidates(
      OffsetDateTime completedBefore,
      int maxAttempts,
      Set<String> nonRetryableFailureCodes,
      String removedBillingKeyFailureCode,
      String notFoundFailureCode,
      OffsetDateTime notFoundRetryableFrom);

  @Query(
      "select a from PaymentAttempt a where a.organizationId = :organizationId and a.environmentId"
          + " IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()} and"
          + " a.mode = :mode and a.status = 'done' and a.completedAt >= :completedFrom and"
          + " a.completedAt < :completedTo order by a.completedAt asc, a.id asc")
  List<PaymentAttempt> findDoneCompletedBetween(
      UUID organizationId, String mode, OffsetDateTime completedFrom, OffsetDateTime completedTo);

  @Query(
      "select a from PaymentAttempt a where a.organizationId = :organizationId and a.mode = :mode"
          + " and a.id in :ids and a.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()}")
  List<PaymentAttempt> findByOrganizationIdAndModeAndIdIn(
      UUID organizationId, String mode, Collection<UUID> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select a from PaymentAttempt a where a.organizationId = :organizationId and a.id = :id and"
          + " a.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()}")
  Optional<PaymentAttempt> findForUpdate(UUID organizationId, UUID id);
}
