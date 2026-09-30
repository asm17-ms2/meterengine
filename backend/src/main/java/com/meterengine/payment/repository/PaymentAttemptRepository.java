package com.meterengine.payment.repository;

import com.meterengine.payment.entity.PaymentAttempt;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

  @Query(
      "select a from PaymentAttempt a where a.organizationId = :organizationId"
          + " and a.invoiceId = :invoiceId and a.status in ('pending', 'done')")
  Optional<PaymentAttempt> findActive(UUID organizationId, UUID invoiceId);

  List<PaymentAttempt> findByOrganizationIdAndInvoiceIdOrderByRequestedAtAsc(
      UUID organizationId, UUID invoiceId);

  boolean existsByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);

  @Query(
      "select a from PaymentAttempt a where a.status = 'pending'"
          + " and a.requestedAt < :requestedBefore order by a.requestedAt")
  List<PaymentAttempt> findPendingRequestedBefore(OffsetDateTime requestedBefore, Limit limit);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PaymentAttempt a set a.status = 'done', a.paymentKey = :paymentKey,"
          + " a.completedAt = :completedAt where a.id = :id and a.status = 'pending'")
  int markDone(UUID id, String paymentKey, OffsetDateTime completedAt);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PaymentAttempt a set a.status = 'failed', a.failureCode = :failureCode,"
          + " a.failureMessage = :failureMessage, a.completedAt = :completedAt"
          + " where a.id = :id and a.status = 'pending'")
  int markFailed(UUID id, String failureCode, String failureMessage, OffsetDateTime completedAt);
}
