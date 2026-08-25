package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricePolicyRepository extends JpaRepository<PricePolicy, PricePolicyId> {

  List<PricePolicy> findByOrganizationId(UUID organizationId);
}
