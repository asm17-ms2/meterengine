package com.meterengine.payment.repository;

import com.meterengine.payment.entity.PaymentAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

  @Query(
      "select a from PaymentAttempt a where a.organizationId = :organizationId"
          + " and a.invoiceId = :invoiceId and a.status in ('pending', 'done')")
  Optional<PaymentAttempt> findActive(UUID organizationId, UUID invoiceId);

  boolean existsByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);
}
