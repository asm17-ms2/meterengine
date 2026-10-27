package com.meterengine.payment.repository;

import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.BillingKeyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillingKeyRepository extends JpaRepository<BillingKey, BillingKeyId> {

  @Query(
      "select b from BillingKey b where b.organizationId = :organizationId and b.mode = :mode and"
          + " b.environmentId IS NOT DISTINCT FROM"
          + " :#{T(com.meterengine.global.security.BusinessEnvironmentContext).environmentId()}"
          + " order by b.customerId")
  List<BillingKey> findByOrganizationIdAndModeOrderByCustomerIdAsc(
      UUID organizationId, String mode);
}
