package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PricePlan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricePlanRepository extends JpaRepository<PricePlan, UUID> {
  List<PricePlan> findByOrganizationIdOrderByEffectiveFromDesc(UUID organizationId);
}
