package com.meterengine.payment.repository;

import com.meterengine.payment.entity.PaymentReconciliationMismatch;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentReconciliationMismatchRepository
    extends JpaRepository<PaymentReconciliationMismatch, UUID> {

  @Transactional
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT INTO payment_reconciliation_mismatch
            (id, organization_id, environment_id, mode, kind, order_id, payment_key, transaction_key,
             meterengine_amount, toss_payments_amount, transacted_at, detected_at)
          VALUES (:#{#mismatch.id}, :#{#mismatch.organizationId}, :#{#mismatch.environmentId}, :#{#mismatch.mode},
                  :#{#mismatch.kind}, :#{#mismatch.orderId}, :#{#mismatch.paymentKey},
                  :#{#mismatch.transactionKey}, :#{#mismatch.meterengineAmount},
                  :#{#mismatch.tossPaymentsAmount}, :#{#mismatch.transactedAt},
                  :#{#mismatch.detectedAt})
          ON CONFLICT ON CONSTRAINT payment_reconciliation_mismatch_kind_order_transaction_unique
          DO NOTHING
          """)
  int insertIfAbsent(PaymentReconciliationMismatch mismatch);

  @Query(
      "select m from PaymentReconciliationMismatch m where m.organizationId = :organizationId and"
          + " m.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()} and"
          + " m.detectedAt >= :detectedFrom and m.detectedAt < :detectedTo order by m.detectedAt"
          + " desc, m.id desc")
  List<PaymentReconciliationMismatch> findDetectedBetween(
      UUID organizationId, OffsetDateTime detectedFrom, OffsetDateTime detectedTo);
}
