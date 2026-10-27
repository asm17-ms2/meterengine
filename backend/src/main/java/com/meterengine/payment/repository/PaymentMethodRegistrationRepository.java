package com.meterengine.payment.repository;

import com.meterengine.payment.entity.PaymentMethodRegistration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentMethodRegistrationRepository
    extends JpaRepository<PaymentMethodRegistration, UUID> {

  Optional<PaymentMethodRegistration> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
      String tokenHash, OffsetDateTime now);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update PaymentMethodRegistration r set r.usedAt = :usedAt"
          + " where r.id = :id and r.usedAt is null and r.expiresAt > :usedAt")
  int markUsed(UUID id, OffsetDateTime usedAt);
}
