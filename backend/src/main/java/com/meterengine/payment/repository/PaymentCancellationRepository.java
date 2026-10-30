package com.meterengine.payment.repository;

import com.meterengine.payment.entity.PaymentCancellation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentCancellationRepository extends JpaRepository<PaymentCancellation, UUID> {

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update PaymentCancellation c set c.status = 'done', c.transactionKey = :transactionKey,"
          + " c.completedAt = :completedAt where c.id = :id and c.status = 'pending'")
  int markDone(UUID id, String transactionKey, OffsetDateTime completedAt);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update PaymentCancellation c set c.status = 'failed', c.failureCode = :failureCode,"
          + " c.failureMessage = :failureMessage, c.completedAt = :completedAt"
          + " where c.id = :id and c.status = 'pending'")
  int markFailed(UUID id, String failureCode, String failureMessage, OffsetDateTime completedAt);

  List<PaymentCancellation> findByPaymentAttemptIdOrderByRequestedAtDesc(UUID paymentAttemptId);

  boolean existsByPaymentAttemptIdAndStatus(UUID paymentAttemptId, String status);

  @Query(
      "select c from PaymentCancellation c where c.status = 'pending'"
          + " and c.requestedAt >= :requestedFrom and c.requestedAt < :requestedBefore"
          + " order by c.requestedAt asc, c.id asc")
  List<PaymentCancellation> findPendingRequestedBetween(
      OffsetDateTime requestedFrom, OffsetDateTime requestedBefore);

  @Query(
      "select count(c) from PaymentCancellation c where c.status = 'pending'"
          + " and c.requestedAt < :requestedBefore")
  long countPendingRequestedBefore(OffsetDateTime requestedBefore);

  @Query(
      "select c from PaymentCancellation c where c.organizationId = :organizationId and"
          + " c.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()} and"
          + " c.mode = :mode and c.status = 'done' and c.completedAt >= :completedFrom and"
          + " c.completedAt < :completedTo order by c.completedAt asc, c.id asc")
  List<PaymentCancellation> findDoneCompletedBetween(
      UUID organizationId, String mode, OffsetDateTime completedFrom, OffsetDateTime completedTo);

  @Query(
      "select coalesce(sum(c.amount), 0L) from PaymentCancellation c"
          + " where c.paymentAttemptId = :paymentAttemptId and c.status = 'done'")
  long sumDoneAmount(UUID paymentAttemptId);
}
