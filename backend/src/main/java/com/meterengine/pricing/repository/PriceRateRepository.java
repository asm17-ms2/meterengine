package com.meterengine.pricing.repository;

import com.meterengine.pricing.entity.PriceRate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRateRepository extends JpaRepository<PriceRate, UUID> {

  boolean existsByOrganizationIdAndBillableMetricCode(
      UUID organizationId, String billableMetricCode);

  List<PriceRate> findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
      UUID organizationId);
}
