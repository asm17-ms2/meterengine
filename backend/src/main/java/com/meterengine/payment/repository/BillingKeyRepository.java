package com.meterengine.payment.repository;

import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.BillingKeyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingKeyRepository extends JpaRepository<BillingKey, BillingKeyId> {

  List<BillingKey> findByOrganizationIdAndModeOrderByCustomerIdAsc(
      UUID organizationId, String mode);
}
