package com.meterengine.payment.repository;

import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.entity.TossPaymentsCredentialId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TossPaymentsCredentialRepository
    extends JpaRepository<TossPaymentsCredential, TossPaymentsCredentialId> {

  @Query(
      "select c from TossPaymentsCredential c where c.organizationId = :organizationId and"
          + " c.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()}"
          + " order by c.mode")
  List<TossPaymentsCredential> findByOrganizationIdOrderByModeAsc(UUID organizationId);

  @Query(
      "select new com.meterengine.payment.entity.TossPaymentsCredentialId(c.organizationId, c.mode,"
          + " c.environmentId) from TossPaymentsCredential c order by c.organizationId asc, c.mode"
          + " asc")
  List<TossPaymentsCredentialId> findAllIds();
}
